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
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.*;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.spec.FactSpecCoordinates;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.factus.*;
import org.factcast.factus.SuppressFactusWarnings.Warning;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.*;
import org.factcast.factus.projection.parameter.HandlerParameterContributor;
import org.factcast.factus.projection.parameter.HandlerParameterContributors;
import org.factcast.factus.projection.parameter.HandlerParameterProvider;
import org.factcast.factus.projection.parameter.HandlerParameterTransformer;
import org.factcast.factus.projection.tx.OpenTransactionAware;
import org.factcast.factus.projection.tx.TransactionAware;
import org.factcast.factus.projection.tx.TransactionException;

@Slf4j
public class ProjectorImpl<A extends Projection> implements Projector<A> {

  private static final Map<Class<? extends Projection>, Map<FactSpecCoordinates, Dispatcher>>
      dispatcherCache = new ConcurrentHashMap<>();
  private final Projection projection;

  @Getter(value = AccessLevel.PROTECTED)
  private final Map<FactSpecCoordinates, Dispatcher> dispatchInfo;

  private final HandlerParameterContributors generalContributors;

  interface TargetObjectResolver extends Function<Projection, Object> {}

  public ProjectorImpl(
      @NonNull Projection p,
      @NonNull EventSerializer serializer,
      @NonNull HandlerParameterContributors parameterContributors) {
    generalContributors = parameterContributors;
    projection = p;
    dispatchInfo =
        dispatcherCache.computeIfAbsent(
            ReflectionTools.getRelevantClass(p), c -> discoverDispatchInfo(serializer, p));
  }

  /**
   * for compatibility
   *
   * @param p
   * @param es
   * @deprecated
   */
  @Deprecated
  public ProjectorImpl(@NonNull Projection p, @NonNull EventSerializer es) {
    this(p, es, new HandlerParameterContributors(es));
  }

  @Override
  public void apply(@NonNull List<Fact> facts) {
    doApply(facts);
  }

  public void doApply(@NonNull List<Fact> facts) {

    beginIfTransactional();

    // remember that IF this fails, we throw an expecption anyway, so that we wont reuse this info
    FactStreamPosition latestSuccessful = null;

    for (Fact f : facts) {

      try {
        callHandlerFor(f);
        latestSuccessful = FactStreamPosition.from(f);
        setFactStreamPositionIfAwareButNotTransactional(latestSuccessful);
      } catch (Exception e) {
        log.trace(
            "returned with Exception {}:",
            latestSuccessful == null ? null : latestSuccessful.factId(),
            e);
        rollbackIfTransactional();
        retryApplicableIfTransactional(facts, f);

        // pass along and potentially rethrow
        projection.onError(e);
        throw ExceptionHelper.toRuntime(e);
      }
    } // end loop

    try {
      // this is something we only do, if the whole batch was successfully applied
      if (projection instanceof TransactionAware && latestSuccessful != null) {
        setFactStreamPositionIfAware(latestSuccessful);
      }
    } catch (Exception e) {

      rollbackIfTransactional();

      // pass along and potentially rethrow
      projection.onError(e);
      throw e;
    }

    try {
      commitIfTransactional();
    } catch (TransactionException e) {
      // pass along and potentially rethrow
      projection.onError(e);
      throw e;
    }
  }

  private void setFactStreamPositionIfAwareButNotTransactional(
      @NonNull FactStreamPosition latestSuccessful) {
    if (!(projection instanceof TransactionAware)) setFactStreamPositionIfAware(latestSuccessful);
  }

  @VisibleForTesting
  void retryApplicableIfTransactional(List<Fact> facts, Fact f) {
    if (projection instanceof TransactionAware) {
      // retry [0,n-1]
      List<Fact> applicableFacts = facts.subList(0, facts.indexOf(f));
      int applicableSize = applicableFacts.size();
      if (applicableSize > 0) {
        log.warn("Exception during batch application, reapplying {} facts.", applicableSize);
        apply(applicableFacts);
      }
    }
  }

  @VisibleForTesting
  void beginIfTransactional() {
    if (projection instanceof TransactionAware) ((TransactionAware) projection).begin();
  }

  @VisibleForTesting
  void rollbackIfTransactional() {
    if (projection instanceof TransactionAware) ((TransactionAware) projection).rollback();
  }

  @VisibleForTesting
  void commitIfTransactional() {
    if (projection instanceof TransactionAware) ((TransactionAware) projection).commit();
  }

  private void setFactStreamPositionIfAware(@NonNull FactStreamPosition latestAttempted) {
    if (projection instanceof FactStreamPositionAware)
      ((FactStreamPositionAware) projection).factStreamPosition(latestAttempted);
  }

  private UUID callHandlerFor(@NonNull Fact f)
      throws InvocationTargetException, IllegalAccessException {
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
    dispatch.invoke(projection, f);

    return factId;
  }

  private Map<FactSpecCoordinates, Dispatcher> discoverDispatchInfo(
      EventSerializer deserializer, Projection p) {
    Map<FactSpecCoordinates, Dispatcher> map = new HashMap<>();

    final HandlerParameterContributors c;

    if (p instanceof OpenTransactionAware<?>) {

      Class<?> clazz = ReflectionTools.getTypeParameter((OpenTransactionAware<?>) p);

      // we have a parameter contributor to add, then
      c =
          generalContributors.withHighestPrio(
              new HandlerParameterContributor() {
                @Nullable
                @Override
                public HandlerParameterProvider providerFor(
                    @NonNull Class<?> type, @NonNull Set<Annotation> annotations) {
                  if (clazz == type)
                    return (f, p) -> ((OpenTransactionAware<?>) p).runningTransaction();
                  else return null;
                }
              });
    } else c = this.generalContributors;

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
                        new Dispatcher(
                            m,
                            HandlerParameterTransformer.forCalling(m, c),
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
  public void onCatchup(@Nullable FactStreamPosition idOfLastFactApplied) {
    // no longer used, might still be interesting as a hook
  }

  /**
   * expensive method that should be used on initialization only
   *
   * @param m
   * @return
   */
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

    void invoke(Projection projection, Fact f) {
      // choose the target object (nested)
      Object targetObject = objectResolver.apply(projection);
      // create actual parameters
      Object[] parameters = transformer.apply(f, projection);
      // fire
      try {
        dispatchMethod.invoke(targetObject, parameters);
      } catch (IllegalAccessException e) {
        throw ExceptionHelper.toRuntime(e);
      } catch (InvocationTargetException e) {
        // unwrap
        throw ExceptionHelper.toRuntime(e.getCause());
      }
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

    @SneakyThrows
    @NonNull
    @VisibleForTesting
    static Class<?> getTypeParameter(@NonNull OpenTransactionAware<?> p) {
      return p.getClass().getMethod("runningTransaction").getReturnType();
    }
  }
}
