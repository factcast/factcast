/*
 * Copyright © 2017-2020 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.factus.projector;

import com.google.common.annotations.VisibleForTesting;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactHeader;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.spec.FactSpecCoordinates;
import org.factcast.factus.*;
import org.factcast.factus.SuppressFactusWarnings.Warning;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.AggregateUtil;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Projection;

@Slf4j
public class ProjectorImpl<A extends Projection> implements Projector<A> {

  private static final Map<Class<? extends Projection>, Map<FactSpecCoordinates, Dispatcher>>
      dispatcherCache = new ConcurrentHashMap<>();
  private final EventSerializer serializer;
  private final Projection projection;
  private final Map<FactSpecCoordinates, Dispatcher> dispatchInfo;
  private final List<ProjectorLens> lenses;
  private UUID lastStateSet = null;

  interface TargetObjectResolver extends Function<Projection, Object> {}

  interface ParameterTransformer extends Function<Fact, Object[]> {}

  @VisibleForTesting
  public ProjectorImpl(EventSerializer ctx, Projection p) {
    serializer = ctx;
    projection = p;
    lenses =
        ProjectorPlugin.discovered.stream()
            .map(plugin -> plugin.lensFor(p))
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    dispatchInfo =
        dispatcherCache.computeIfAbsent(
            ReflectionTools.getRelevantClass(p), c -> discoverDispatchInfo(ctx, p));
  }

  private ParameterTransformer createParameterTransformer(Method m) {

    Class<?>[] parameterTypes = m.getParameterTypes();
    return p -> {
      Object[] parameters = new Object[parameterTypes.length];

      for (int i = 0; i < parameterTypes.length; i++) {
        Class<?> type = parameterTypes[i];
        Function<Fact, ?> transformer = createSingleParameterTransformer(m, type);
        parameters[i] = transformer.apply(p);
      }
      return parameters;
    };
  }

  @SuppressWarnings("unchecked")
  private Function<Fact, ?> createSingleParameterTransformer(Method m, Class<?> type) {

    if (EventObject.class.isAssignableFrom(type)) {
      return p -> serializer.deserialize((Class<? extends EventObject>) type, p.jsonPayload());
    }

    if (Fact.class == type) {
      return p -> p;
    }

    if (FactHeader.class == type) {
      return Fact::header;
    }

    if (UUID.class == type) {
      return Fact::id;
    }

    for (ProjectorLens p : lenses) {
      Function<Fact, ?> transformerOrNull = p.parameterTransformerFor(type);
      if (transformerOrNull != null) {
        // first one wins
        return transformerOrNull;
      }
    }

    throw new InvalidHandlerDefinition(
        "Don't know how resolve " + type + " from a Fact for a parameter to method:\n " + m);
  }

  @Override
  public void apply(@NonNull Fact f) {
    UUID factId = f.id();
    log.trace("Dispatching fact {}", factId);
    FactSpecCoordinates coords = FactSpecCoordinates.from(f);
    Dispatcher dispatch = dispatchInfo.get(coords);
    if (dispatch == null) {
      // try to find one with no version as a fallback
      dispatch = dispatchInfo.get(coords.withVersion(0));
    }

    if (dispatch == null) {
      InvalidHandlerDefinition ihd =
          new InvalidHandlerDefinition("Unexpected Fact coordinates: '" + coords + "'");
      projection.onError(ihd);
      throw ihd;
    }

    try {
      lenses.forEach(l -> l.beforeFactProcessing(f));
      dispatch.invoke(projection, f, createParameterTransformer(dispatch.dispatchMethod));

      if (projection instanceof FactStreamPositionAware) {
        boolean skip =
            lenses.stream()
                .map(ProjectorLens::skipStateUpdate)
                .reduce(false, (r, lens) -> r || lens);

        if (!skip) {
          ((FactStreamPositionAware) projection).factStreamPosition(factId);
          lastStateSet = factId;
        }
      }

      lenses.forEach(l -> l.afterFactProcessing(f));

    } catch (InvocationTargetException | IllegalAccessException e) {
      log.trace("returned with Exception {}:", factId, e);

      // inform the lenses
      lenses.forEach(l -> l.afterFactProcessingFailed(f, e));

      // pass along and potentially rethrow
      projection.onError(e);
      throw new IllegalArgumentException(e);
    } catch (Exception e) {

      // inform the lenses
      lenses.forEach(l -> l.afterFactProcessingFailed(f, e));

      // pass along and potentially rethrow
      projection.onError(e);
      throw e;
    }
  }

  private Map<FactSpecCoordinates, Dispatcher> discoverDispatchInfo(
      EventSerializer deserializer, Projection p) {
    Map<FactSpecCoordinates, Dispatcher> map = new HashMap<>();

    Collection<CallTarget> relevantClasses = ReflectionTools.getRelevantClasses(p);
    relevantClasses.forEach(
        callTarget -> {
          Set<Method> methods = ReflectionTools.collectMethods(callTarget.clazz);
          methods.stream()
              .filter(this::isEventHandlerMethod)
              .forEach(
                  m -> {
                    FactSpec fs = ReflectionTools.discoverFactSpec(m);
                    FactSpecCoordinates key = FactSpecCoordinates.from(fs);

                    Dispatcher dispatcher =
                        new Dispatcher(m, callTarget.resolver, fs, deserializer);
                    Dispatcher before = map.put(key, dispatcher);
                    if (before != null) {
                      throw new InvalidHandlerDefinition(
                          "Duplicate Handler method found for spec '"
                              + key
                              + "':\n "
                              + m
                              + "\n clashes with\n "
                              + before.dispatchMethod());
                    }

                    log.debug("Discovered Event handling method " + m.toString());
                    m.setAccessible(true);
                  });
        });

    if (map.isEmpty()) {
      throw new InvalidHandlerDefinition("No handler methods discovered on " + p.getClass());
    }

    return map;
  }

  @Override
  public List<FactSpec> createFactSpecs() {
    List<FactSpec> discovered =
        dispatchInfo.values().stream().map(d -> d.spec.copy()).collect(Collectors.toList());

    if (projection instanceof Aggregate) {
      UUID aggId = AggregateUtil.aggregateId((Aggregate) projection);
      for (FactSpec factSpec : discovered) {
        factSpec.aggId(aggId);
      }
    }

    @NonNull List<FactSpec> ret = projection.postprocess(discovered);
    //noinspection ConstantConditions
    if (ret == null || ret.isEmpty()) {
      throw new InvalidHandlerDefinition(
          "No FactSpecs discovered from "
              + projection.getClass()
              + ". Either add handler methods or implement postprocess(List<FactSpec)");
    }
    return Collections.unmodifiableList(ret);
  }

  @Override
  public void onCatchup(UUID idOfLastFactApplied) {
    // needs to be taken care if BEFORE delegating to the lenses as they might commit/execute and we
    // want that state in there.
    if (projection instanceof FactStreamPositionAware) {
      if (idOfLastFactApplied != null && (idOfLastFactApplied != lastStateSet)) {
        ((FactStreamPositionAware) projection).factStreamPosition(idOfLastFactApplied);
      }
    }

    for (ProjectorLens lens : lenses) {
      lens.onCatchup(projection);
    }
  }

  @VisibleForTesting
  boolean isEventHandlerMethod(Method m) {
    if (m.getAnnotation(Handler.class) != null || m.getAnnotation(HandlerFor.class) != null) {

      if (!m.getReturnType().equals(void.class)) {
        throw new InvalidHandlerDefinition(
            "Handler methods must return void, but \n "
                + m
                + "\n returns '"
                + m.getReturnType()
                + "'");
      }

      if (m.getParameterCount() == 0) {
        throw new InvalidHandlerDefinition(
            "Handler methods must have at least one parameter: " + m);
      }

      if (Modifier.isPublic(m.getModifiers())) {
        if (!Warning.PUBLIC_HANDLER_METHOD.isSuppressedOn(m)) {
          log.warn("Handler methods should not be public: " + m);
        }
      }

      for (Class<?> type : m.getParameterTypes()) {
        // trigger transformer creation in order to fail fast
        createSingleParameterTransformer(m, type);
      }

      // exclude MockitoMocks
      return !m.getDeclaringClass().getName().contains("$MockitoMock");
    }
    return false;
  }

  @Value
  @VisibleForTesting
  static class Dispatcher {

    @NonNull Method dispatchMethod;

    @NonNull ProjectorImpl.TargetObjectResolver objectResolver;

    @NonNull FactSpec spec;

    @NonNull EventSerializer deserializer;

    void invoke(Projection projection, Fact f, ParameterTransformer pt)
        throws InvocationTargetException, IllegalAccessException {
      Object targetObject = objectResolver.apply(projection);
      Object[] parameters = pt.apply(f);
      dispatchMethod.invoke(targetObject, parameters);
    }
  }

  @Value
  static class CallTarget {
    Class<?> clazz;

    TargetObjectResolver resolver;
  }

  static class ReflectionTools {
    private static Class<? extends Projection> getRelevantClass(@NonNull Projection p) {
      Class<? extends Projection> c = p.getClass();
      return getRelevantClass(c);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Projection> getRelevantClass(
        @NonNull Class<? extends Projection> c) {
      while (c.getName().contains("$$EnhancerBySpring") || c.getName().contains("CGLIB")) {
        c = (Class<? extends Projection>) c.getSuperclass();
      }
      return c;
    }

    private static Set<Method> collectMethods(Class<?> clazz) {
      if (clazz == null) {
        return Collections.emptySet();
      }

      HashSet<Method> m = new HashSet<>();
      m.addAll(Arrays.asList(clazz.getMethods()));
      m.addAll(Arrays.asList(clazz.getDeclaredMethods()));
      m.addAll(collectMethods(clazz.getSuperclass()));
      return m;
    }

    private static FactSpec discoverFactSpec(Method m) {

      HandlerFor handlerFor = m.getAnnotation(HandlerFor.class);
      if (handlerFor != null) {
        return addOptionalFilterInfo(
            m, FactSpec.ns(handlerFor.ns()).type(handlerFor.type()).version(handlerFor.version()));
      }

      List<Class<?>> eventPojoTypes =
          Arrays.stream(m.getParameterTypes())
              .filter(EventObject.class::isAssignableFrom)
              .collect(Collectors.toList());

      if (eventPojoTypes.isEmpty()) {
        throw new InvalidHandlerDefinition(
            "Cannot introspect FactSpec from "
                + m
                + ". Either use @HandlerFor or pass an EventPojo as a parameter.");
      } else {
        if (eventPojoTypes.size() > 1) {
          throw new InvalidHandlerDefinition(
              "Multiple EventPojo Parameters. Cannot introspect FactSpec from " + m);
        } else {
          Class<?> eventPojoType = eventPojoTypes.get(0);
          return addOptionalFilterInfo(m, FactSpec.from(eventPojoType));
        }
      }
    }

    @VisibleForTesting
    static FactSpec addOptionalFilterInfo(Method m, FactSpec spec) {
      FilterByMetas attributes = m.getAnnotation(FilterByMetas.class);
      if (attributes != null)
        Arrays.stream(attributes.value()).forEach(a -> spec.meta(a.key(), a.value()));

      FilterByMeta attribute = m.getAnnotation(FilterByMeta.class);
      if (attribute != null) spec.meta(attribute.key(), attribute.value());

      FilterByAggId aggregateId = m.getAnnotation(FilterByAggId.class);
      if (aggregateId != null) spec.aggId(UUID.fromString(aggregateId.value()));

      FilterByScript filterByScript = m.getAnnotation(FilterByScript.class);
      if (filterByScript != null)
        spec.filterScript(org.factcast.core.spec.FilterScript.js(filterByScript.value()));

      return spec;
    }

    private static Collection<CallTarget> getRelevantClasses(Projection p) {
      return getRelevantClasses(new CallTarget(getRelevantClass(p.getClass()), o -> o));
    }

    private static Collection<CallTarget> getRelevantClasses(CallTarget root) {
      List<CallTarget> classes = new LinkedList<>();
      classes.add(root);
      Arrays.stream(root.clazz().getDeclaredClasses())
          .filter(c -> !Modifier.isStatic(c.getModifiers()))
          .forEach(
              c ->
                  classes.addAll(
                      getRelevantClasses(
                          new CallTarget(c, p -> resolveTargetObject(root.resolver.apply(p), c)))));
      return classes;
    }

    @VisibleForTesting
    static Object resolveTargetObject(Object parent, Class<?> c) {
      try {
        Constructor<?> ctor;
        try {
          ctor = c.getDeclaredConstructor(parent.getClass());
          ctor.setAccessible(true);
          return ctor.newInstance(parent);

        } catch (NoSuchMethodException e) {
          // static class
          ctor = c.getDeclaredConstructor();
          ctor.setAccessible(true);
          return ctor.newInstance();
        }

      } catch (InstantiationException
          | IllegalAccessException
          | NoSuchMethodException
          | InvocationTargetException e) {
        throw new IllegalStateException("Cannot instantiate " + c, e);
      }
    }
  }
}
