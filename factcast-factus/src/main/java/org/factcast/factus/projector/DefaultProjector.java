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
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.core.FactHeader;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.spec.FactSpecCoordinates;
import org.factcast.factus.Handler;
import org.factcast.factus.HandlerFor;
import org.factcast.factus.SuppressFactusWarnings.Warning;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.AggregateUtil;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projection.StateAware;

@Slf4j
public class DefaultProjector<A extends Projection> implements Projector<A> {

  private final Projection projection;

  private static final Map<Class<? extends Projection>, Map<FactSpecCoordinates, Dispatcher>>
      cache = new ConcurrentHashMap<>();

  interface TargetObjectResolver extends Function<Projection, Object> {}

  interface ParameterTransformer extends Function<Fact, Object[]> {}

  private final Map<FactSpecCoordinates, Dispatcher> dispatchInfo;

  @VisibleForTesting
  public DefaultProjector(EventSerializer ctx, Projection p) {
    projection = p;
    dispatchInfo = cache.computeIfAbsent(getRelevantClass(p), c -> discoverDispatchInfo(ctx, p));
  }

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

  @Override
  public void apply(@NonNull Fact f) {
    log.trace("Dispatching fact {}", f.id());
    val coords = FactSpecCoordinates.from(f);
    val dispatch = dispatchInfo.get(coords);
    if (dispatch == null) {
      val ihd = new InvalidHandlerDefinition("Unexpected Fact coordinates: '" + coords + "'");
      projection.onError(ihd);
      throw ihd;
    }

    try {
      dispatch.invoke(projection, f);
      if (projection instanceof StateAware) {
        ((StateAware) projection).state(f.id());
      }

    } catch (InvocationTargetException | IllegalAccessException e) {
      log.trace("returned with Exception {}: {}", f.id(), e);
      // pass along and potentially rethrow
      projection.onError(e);
      throw new IllegalArgumentException(e);
    } catch (Throwable e) {
      // pass along and potentially rethrow
      projection.onError(e);
      throw e;
    }
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

    val ret = projection.postprocess(discovered);
    if (ret == null || ret.isEmpty()) {
      throw new InvalidHandlerDefinition(
          "No FactSpecs discovered from "
              + projection.getClass()
              + ". Either add handler methods or implement postprocess(List<FactSpec)");
    }
    return Collections.unmodifiableList(ret);
  }

  // --------------------------------------------------------

  @Value
  @VisibleForTesting
  static class Dispatcher {

    Method dispatchMethod;

    TargetObjectResolver objectResolver;

    ParameterTransformer parameterTransformer;

    FactSpec spec;

    EventSerializer deserializer;

    void invoke(Projection projection, Fact f)
        throws InvocationTargetException, IllegalAccessException {
      log.trace("Entering Dispatcher.invoke for {}", f.id());
      Object targetObject = objectResolver.apply(projection);
      log.trace("Target: {}", targetObject);
      Object[] parameters = parameterTransformer.apply(f);
      log.trace("Params: {}", Arrays.toString(parameters));
      dispatchMethod.invoke(targetObject, parameters);
    }
  }

  private static Map<FactSpecCoordinates, Dispatcher> discoverDispatchInfo(
      EventSerializer deserializer, Projection p) {
    Map<FactSpecCoordinates, Dispatcher> map = new HashMap<>();

    Collection<CallTarget> relevantClasses = getRelevantClasses(p);
    relevantClasses.forEach(
        callTarget -> {
          Set<Method> methods = collectMethods(callTarget.clazz);
          methods.stream()
              .filter(DefaultProjector::isEventHandlerMethod)
              .forEach(
                  m -> {
                    FactSpec fs = discoverFactSpec(m);
                    FactSpecCoordinates key = FactSpecCoordinates.from(fs);

                    Dispatcher dispatcher =
                        new Dispatcher(
                            m,
                            callTarget.resolver,
                            createParameterTransformer(deserializer, m),
                            fs,
                            deserializer);
                    val before = map.put(key, dispatcher);
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
      return FactSpec.ns(handlerFor.ns()).type(handlerFor.type()).version(handlerFor.version());
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
        return FactSpec.from(eventPojoType);
      }
    }
  }

  private static ParameterTransformer createParameterTransformer(EventSerializer ctx, Method m) {

    Class<?>[] parameterTypes = m.getParameterTypes();
    return p -> {
      Object[] parameters = new Object[parameterTypes.length];

      for (int i = 0; i < parameterTypes.length; i++) {
        Class<?> type = parameterTypes[i];
        Function<Fact, Object> transformer = createSingleParameterTransformer(m, ctx, type);
        parameters[i] = transformer.apply(p);
      }
      return parameters;
    };
  }

  @SuppressWarnings("unchecked")
  private static Function<Fact, Object> createSingleParameterTransformer(
      Method m, EventSerializer deserializer, Class<?> type) {
    if (EventObject.class.isAssignableFrom(type)) {
      return p -> deserializer.deserialize((Class<? extends EventObject>) type, p.jsonPayload());
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

    throw new InvalidHandlerDefinition(
        "Don't know how resolve " + type + " from a Fact for a parameter to method:\n " + m);
  }

  @Value
  static class CallTarget {
    Class<?> clazz;

    TargetObjectResolver resolver;
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

  @VisibleForTesting
  static boolean isEventHandlerMethod(Method m) {
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
<<<<<<< HEAD
        if (!Warning.PUBLIC_HANDLER_METHOD.isSuppressedOn(m))
=======
        if (!Warning.PUBLIC_HANDLER_METHOD.isSuppressed(
            m.getAnnotation(SuppressFactusWarnings.class)))
>>>>>>> 7e5f425adc06d1bf5d0adc0781ff6eec41d173bd
          log.warn("Handler methods should not be public: " + m);
      }

      for (Class<?> type : m.getParameterTypes()) {
        // trigger transformer creation in order to fail fast
        createSingleParameterTransformer(m, null, type);
      }

      // exclude MockitoMocks
      return !m.getDeclaringClass().getName().contains("$MockitoMock");
    }
    return false;
  }
}
