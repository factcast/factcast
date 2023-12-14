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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.spec.FactSpecCoordinates;
import org.factcast.factus.*;
import org.factcast.factus.SuppressFactusWarnings.Warning;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.*;
import org.factcast.factus.projection.parameter.HandlerParameterContributors;
import org.factcast.factus.projection.parameter.HandlerParameterTransformer;

@Slf4j
public class ProjectorImpl<A extends Projection, T extends ProjectorContext>
    implements Projector<A, T> {

  private static final Map<Class<? extends Projection>, Map<FactSpecCoordinates, Dispatcher>>
      dispatcherCache = new ConcurrentHashMap<>();
  private final Projection projection;
  private final Map<FactSpecCoordinates, Dispatcher> dispatchInfo;
  private final HandlerParameterContributors contributors;

  // maybe remove? private UUID lastStateSet = null;

  // for dealing with nested objects
  interface TargetObjectResolver extends Function<Projection, Object> {}

  public ProjectorImpl(
      @NonNull Projection p,
      @NonNull EventSerializer serializer,
      @NonNull HandlerParameterContributors parameterContributors) {
    contributors = parameterContributors;
    projection = p;
    dispatchInfo =
        dispatcherCache.computeIfAbsent(
            ReflectionTools.getRelevantClass(p),
            c -> discoverDispatchInfo(p, parameterContributors, serializer));
  }

  @Override
  public void apply(@NonNull Fact f) {
    apply(Collections.singleton(f));
  }

  /**
   * @param f
   * @return id of the fact applied
   */
  @SneakyThrows
  private UUID doApply(Fact f, T ctx) {
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
    // TODO
    dispatch.invoke(projection, f, ctx);

    return factId;
  }

  @Override
  @SneakyThrows
  public void apply(@NonNull Iterable<Fact> facts) {
    if (projection instanceof ContextualProjection) {
      @SuppressWarnings("unchecked")
      ContextualProjection<T> cp = (ContextualProjection<T>) projection;

      T ctx = cp.begin();
      AtomicReference<UUID> last = new AtomicReference<>();
      facts.forEach(f -> last.set(doApply(f, ctx)));
      cp.factStreamPosition(last.get(), ctx);
      cp.commit(ctx);
      // TODO err handling / retry to last...

    } else {
      // just a basic projection, no context
      facts.forEach(f -> doApply(f, null));
    }
  }

  private static Map<FactSpecCoordinates, Dispatcher> discoverDispatchInfo(
      Projection p, HandlerParameterContributors contributors, EventSerializer deserializer) {
    Map<FactSpecCoordinates, Dispatcher> map = new HashMap<>();

    Collection<CallTarget> relevantClasses = ReflectionTools.getRelevantClasses(p);
    relevantClasses.forEach(
        callTarget -> {
          Set<Method> methods = ReflectionTools.collectMethods(callTarget.clazz);
          methods.stream()
              .filter(m -> isEventHandlerMethod(m, contributors))
              .forEach(
                  m -> {
                    FactSpec fs = ReflectionTools.discoverFactSpec(m);
                    FactSpecCoordinates key = FactSpecCoordinates.from(fs);

                    Dispatcher dispatcher =
                        new Dispatcher(
                            m,
                            HandlerParameterTransformer.forCalling(m, contributors),
                            callTarget.resolver,
                            fs,
                            deserializer);
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
    // needs to be taken care of BEFORE delegating to the lenses as they might commit/execute and we
    // want that state in there.

    // TODO is this still necessary?
    //        if (projection instanceof FactStreamPositionAware) {
    //            if (idOfLastFactApplied != null && (idOfLastFactApplied != lastStateSet)) {
    //                ((FactStreamPositionAware)
    // projection).factStreamPosition(idOfLastFactApplied);
    //            }
    //        }

    // TODO does not need replacement, i guess
    //        for (ProjectorLens lens : lenses) {
    //            lens.onCatchup(projection);
    //        }
  }

  /**
   * expensive method that should be used on initialization only
   *
   * @param m
   * @return
   */
  @VisibleForTesting
  static boolean isEventHandlerMethod(Method m, HandlerParameterContributors contributors) {
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

      // TODO creat HPTrans for fast failure
      //            for (Class<?> type : m.getParameterTypes()) {
      //                // trigger transformer creation in order to fail fast
      //                createSingleParameterTransformer(m, type);
      //            }

      // exclude MockitoMocks
      return !m.getDeclaringClass().getName().contains("$MockitoMock");
    }
    return false;
  }

  @Value
  @VisibleForTesting
  static class Dispatcher {

    @NonNull Method dispatchMethod;
    @NonNull HandlerParameterTransformer transformer;

    @NonNull TargetObjectResolver objectResolver;

    @NonNull FactSpec spec;

    @NonNull EventSerializer deserializer;

    void invoke(Projection projection, Fact f, @Nullable ProjectorContext ctx)
        throws InvocationTargetException, IllegalAccessException {
      // choose the target object (nested)
      Object targetObject = objectResolver.apply(projection);
      // create actual parameters
      Object[] parameters = transformer.apply(f, ctx);
      // fire
      dispatchMethod.invoke(targetObject, parameters);
    }
  }

  @Value
  static class CallTarget {
    Class<?> clazz;

    TargetObjectResolver resolver;
  }

  @UtilityClass
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
