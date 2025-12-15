/*
 * Copyright © 2017-2025 factcast.org
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

import static java.util.Collections.emptySet;

import com.google.common.annotations.VisibleForTesting;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.*;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.spec.*;
import org.factcast.factus.*;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.*;
import org.factcast.factus.projection.parameter.*;
import org.factcast.factus.projection.tx.OpenTransactionAware;
import org.springframework.util.StringUtils;

@Slf4j
@UtilityClass
public class ReflectionUtils {

  private static final Map<Class<? extends Projection>, Map<FactSpecCoordinates, Dispatcher>>
      dispatcherCache = new ConcurrentHashMap<>();

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
      return emptySet();
    }

    HashSet<Method> m = new HashSet<>();
    m.addAll(Arrays.asList(clazz.getMethods()));
    m.addAll(Arrays.asList(clazz.getDeclaredMethods()));
    m.addAll(collectMethods(clazz.getSuperclass()));
    return m;
  }

  private static FactSpec discoverFactSpec(Projection p, Method m) {

    HandlerFor handlerFor = m.getAnnotation(HandlerFor.class);
    if (handlerFor != null) {
      return addOptionalFilterInfo(
          m, FactSpec.ns(handlerFor.ns()).type(handlerFor.type()).version(handlerFor.version()));
    }

    List<Class<?>> eventPojoTypes =
        Arrays.stream(m.getParameterTypes())
            .filter(org.factcast.factus.event.EventObject.class::isAssignableFrom)
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
        FactSpec fromTargetType = FactSpec.from(eventPojoType);

        // yes, order is important :D
        OverrideNamespaces overridesOnMethod = m.getAnnotation(OverrideNamespaces.class);
        if (overridesOnMethod != null) {
          throw new IllegalArgumentException(
              "Only one single @OverrideNamespace is allowed on method level");
        }

        OverrideNamespace overrideOnMethod = m.getAnnotation(OverrideNamespace.class);
        if (overrideOnMethod != null) {
          return overrideNamespaceFromMethodAnnotation(
              m, overrideOnMethod, eventPojoType, fromTargetType);
        }

        return addOptionalFilterInfo(
            m, overrideNamespaceFromTypeAnnotation(p, eventPojoType, fromTargetType));
      }
    }
  }

  @VisibleForTesting
  static FactSpec overrideNamespaceFromTypeAnnotation(
      Projection p, Class<?> eventPojoType, FactSpec fromTargetType) {

    Arrays.stream(p.getClass().getInterfaces())
        .filter(
            i ->
                i.getAnnotation(OverrideNamespace.class) != null
                    || i.getAnnotation(OverrideNamespaces.class) != null)
        .findFirst()
        .ifPresent(
            i -> {
              throw new InvalidHandlerDefinition(
                  "@OverrideNamespace(s) is only allowed on non-interface types "
                      + p.getClass()
                      + " implementing "
                      + i);
            });

    Map<Class<?>, String> overrides = buildNamespaceOverrides(p.getClass());
    String override = overrides.get(eventPojoType);
    if (override != null) return fromTargetType.withNs(override);
    else return fromTargetType;
  }

  @VisibleForTesting
  static Map<Class<?>, String> buildNamespaceOverrides(Class<?> p) {
    if (p == null || !Projection.class.isAssignableFrom(p)) return new HashMap<>();

    Map<Class<?>, String> ret = buildNamespaceOverrides(p.getSuperclass());

    OverrideNamespace single = p.getAnnotation(OverrideNamespace.class);
    if (single != null) ret.put(single.type(), single.ns());

    OverrideNamespaces container = p.getAnnotation(OverrideNamespaces.class);
    if (container != null) {
      OverrideNamespace[] overrides = container.value();
      if (overrides != null) Arrays.stream(overrides).forEach(s -> ret.put(s.type(), s.ns()));
    }

    return ret;
  }

  @VisibleForTesting
  static FactSpec overrideNamespaceFromMethodAnnotation(
      Method m, OverrideNamespace annotation, Class<?> eventPojoType, FactSpec fromTargetType) {
    String newNs = annotation.ns();
    Class<? extends org.factcast.factus.event.EventObject> forType = annotation.type();

    if (newNs.isEmpty())
      throw new InvalidHandlerDefinition(
          "A valid namespace must be provided for a @OverrideNamespace annotation on " + m);

    if (!forType.equals(OverrideNamespace.DISCOVER) && forType != eventPojoType)
      throw new InvalidHandlerDefinition(
          "@OverrideNamespace defined for a different type than what the parameter suggests " + m);

    return addOptionalFilterInfo(m, fromTargetType.withNs(newNs));
  }

  @VisibleForTesting
  static FactSpec addOptionalFilterInfo(@NonNull Method m, @NonNull FactSpec spec) {
    spec = filterByMeta(m, spec);
    spec = filterByMetaExists(m, spec);
    spec = filterByMetaDoesNotExist(m, spec);
    spec = filterByAggIds(m, spec);
    spec = filterByScript(m, spec);

    checkFilterByAggIdProperty(m, spec);
    return spec;
  }

  private static FactSpec filterByScript(@NonNull Method m, @NonNull FactSpec spec) {
    FilterByScript filterByScript = m.getAnnotation(FilterByScript.class);
    if (filterByScript != null)
      spec = spec.filterScript(org.factcast.core.spec.FilterScript.js(filterByScript.value()));
    return spec;
  }

  private static FactSpec filterByAggIds(@NonNull Method m, @NonNull FactSpec spec) {
    FilterByAggId aggregateIds = m.getAnnotation(FilterByAggId.class);
    if (aggregateIds != null) {
      for (String aggId : aggregateIds.value()) {
        spec = spec.aggId(UUID.fromString(aggId));
      }
    }
    return spec;
  }

  /**
   * this will only check for applicability of the FilterByAggIdProperty annotation. The actual pair
   * needs to be created from the instance, rather than statically, as the value (the aggregate id)
   * is dynamic.
   */
  @VisibleForTesting
  static void checkFilterByAggIdProperty(@NonNull Method m, @NonNull FactSpec spec) {
    FilterByAggIdProperty annotation = m.getAnnotation(FilterByAggIdProperty.class);
    if (annotation != null) {

      if (!Aggregate.class.isAssignableFrom(m.getDeclaringClass()))
        throw new IllegalAnnotationForTargetClassException(
            "FilterByAggIdProperty can only be used on classes extending Aggregate, but was found on "
                + m.toString());

      if (m.getAnnotation(HandlerFor.class) != null) {
        log.warn(
            "Using FilterByAggIdProperty on HandlerFor method "
                + m.toString()
                + " which means the property cannot be verified.");
        return;
      }

      // check applicability if param is eventObject
      verifyUuidPropertyExpressionAgainstClass(annotation.value(), findEventObjectParameterType(m));
    }
  }

  @VisibleForTesting
  @SuppressWarnings("unchecked")
  static <E extends org.factcast.factus.event.EventObject> Class<E> findEventObjectParameterType(
      @NonNull Method m) {
    List<Class<? extends org.factcast.factus.event.EventObject>> found =
        Arrays.stream(m.getParameterTypes())
            .filter(org.factcast.factus.event.EventObject.class::isAssignableFrom)
            .map(p -> (Class<? extends org.factcast.factus.event.EventObject>) p)
            .collect(Collectors.toList());

    if (found.isEmpty()) throw new NoEventObjectParameterFoundException(m);
    else if (found.size() > 1) throw new AmbiguousObjectParameterFoundException(m);
    return (Class<E>) found.get(0);
  }

  public static Map<FactSpecCoordinates, Dispatcher> getDispatcherInfo(
      @NonNull Projection p, HandlerParameterContributors generalContributors) {
    return dispatcherCache.computeIfAbsent(
        ReflectionUtils.getRelevantClass(p), c -> discoverDispatchInfo(p, generalContributors));
  }

  @SuppressWarnings("java:S3011")
  private Map<FactSpecCoordinates, Dispatcher> discoverDispatchInfo(
      Projection p, HandlerParameterContributors generalContributors) {
    Map<FactSpecCoordinates, Dispatcher> map = new HashMap<>();

    final HandlerParameterContributors c;

    if (p instanceof OpenTransactionAware<?>) {

      Class<?> clazz = ReflectionUtils.getTypeParameter((OpenTransactionAware<?>) p);

      // we have a parameter contributor to add, then
      c =
          generalContributors.withHighestPrio(
              new HandlerParameterContributor() {
                @Nullable
                @Override
                public HandlerParameterProvider providerFor(
                    @NonNull Class<?> type,
                    @Nullable Type genericType,
                    @NonNull Set<Annotation> annotations) {
                  if (clazz == type)
                    return (s, f, p) -> ((OpenTransactionAware<?>) p).runningTransaction();
                  else return null;
                }
              });
    } else c = generalContributors;

    Collection<CallTarget> relevantClasses = ReflectionUtils.getRelevantClasses(p);
    relevantClasses.forEach(
        callTarget -> {
          Set<Method> methods = ReflectionUtils.collectMethods(callTarget.clazz());
          methods.stream()
              .filter(ReflectionUtils::isEventHandlerMethod)
              .forEach(
                  m -> {
                    FactSpec fs = ReflectionUtils.discoverFactSpec(p, m);
                    FactSpecCoordinates key = FactSpecCoordinates.from(fs);

                    Dispatcher dispatcher =
                        new Dispatcher(
                            m,
                            HandlerParameterTransformer.forCalling(m, c),
                            callTarget.resolver(),
                            fs);
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

                    log.debug("Discovered Event handling method {}", m.toString());

                    m.setAccessible(true);
                  });
        });

    if (map.isEmpty()) {
      throw new InvalidHandlerDefinition("No handler methods discovered on " + p.getClass());
    }

    return map;
  }

  /**
   * expensive method that should be used on initialization only
   *
   * @param m
   * @return boolean
   */
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

      if (Modifier.isPublic(m.getModifiers())
          && !SuppressFactusWarnings.Warning.PUBLIC_HANDLER_METHOD.isSuppressedOn(m)) {
        log.warn("Handler methods should not be public: {}", m);
      }

      // exclude MockitoMocks
      return !m.getDeclaringClass().getName().contains("$MockitoMock");
    }
    return false;
  }

  @NonNull
  @SneakyThrows
  @SuppressWarnings("java:S3011")
  public static <P extends SnapshotProjection> P instantiate(Class<P> projectionClass) {
    log.trace("Creating initial projection version for {}", projectionClass);
    Constructor<P> con = projectionClass.getDeclaredConstructor();
    con.setAccessible(true);
    return con.newInstance();
  }

  static class NoEventObjectParameterFoundException extends IllegalArgumentException {
    private NoEventObjectParameterFoundException(@NonNull Method m) {
      super("No EventObject parameter type found on method " + m);
    }
  }

  static class AmbiguousObjectParameterFoundException extends IllegalArgumentException {
    private AmbiguousObjectParameterFoundException(@NonNull Method m) {
      super("Ambiguous EventObject parameter type found on method " + m);
    }
  }

  /**
   * resolves the path through the Object graph starting from the eventObjectType to make sure that
   * the path is valid and the resulting return type is UUID.
   *
   * @param value the path in dot-notation, case-sensitive
   * @param eventObjectType the root pojo to resolve the path on
   * @throws IllegalAggregateIdPropertyPathException when path does not exist, or does not resolve
   *     to UUID type
   */
  @VisibleForTesting
  static void verifyUuidPropertyExpressionAgainstClass(
      @NonNull String value, @NonNull Class<? extends EventObject> eventObjectType)
      throws IllegalAggregateIdPropertyPathException {
    String[] path = value.split("\\.");
    Class<?> type = eventObjectType;
    for (int i = 0; i <= path.length - 1; i++) {
      try {
        // we're expecting JavaBeans-specification-type getters here
        type = type.getMethod("get" + StringUtils.capitalize(path[i])).getReturnType();
      } catch (NoSuchMethodException e) {
        throw new IllegalAggregateIdPropertyPathException(
            "Cannot resolve property "
                + path[i]
                + " on type "
                + type
                + " (full path='"
                + value
                + "' from "
                + type
                + ")");
      }
    }

    if (!UUID.class.isAssignableFrom(type)) {
      throw new IllegalAggregateIdPropertyPathException(
          "Encountered non-UUID type at " + value + " on type " + type);
    }
  }

  private static FactSpec filterByMetaDoesNotExist(@NonNull Method m, @NonNull FactSpec spec) {
    FilterByMetaDoesNotExistContainer doesNotExistContainer =
        m.getAnnotation(FilterByMetaDoesNotExistContainer.class);
    if (doesNotExistContainer != null) {
      for (FilterByMetaDoesNotExist notExists : doesNotExistContainer.value()) {
        spec = addFilterByMetaDoesNotExist(spec, notExists);
      }
    }
    FilterByMetaDoesNotExist attribute = m.getAnnotation(FilterByMetaDoesNotExist.class);
    if (attribute != null) spec = addFilterByMetaDoesNotExist(spec, attribute);
    return spec;
  }

  private static FactSpec filterByMetaExists(@NonNull Method m, @NonNull FactSpec spec) {
    FilterByMetaExistsContainer existsContainer =
        m.getAnnotation(FilterByMetaExistsContainer.class);
    if (existsContainer != null) {
      for (FilterByMetaExists exists : existsContainer.value()) {
        spec = addFilterByMetaExists(spec, exists);
      }
    }
    FilterByMetaExists exists = m.getAnnotation(FilterByMetaExists.class);
    if (exists != null) spec = addFilterByMetaExists(spec, exists);
    return spec;
  }

  private static FactSpec filterByMeta(@NonNull Method m, @NonNull FactSpec spec) {
    FilterByMetas metas = m.getAnnotation(FilterByMetas.class);
    if (metas != null) {
      for (FilterByMeta meta : metas.value()) {
        spec = addFilterByMeta(spec, meta);
      }
    }
    FilterByMeta meta = m.getAnnotation(FilterByMeta.class);
    if (meta != null) spec = addFilterByMeta(spec, meta);
    return spec;
  }

  private static FactSpec addFilterByMetaDoesNotExist(
      @NonNull FactSpec spec, @NonNull FilterByMetaDoesNotExist notExists) {
    return spec.metaDoesNotExist(notExists.value());
  }

  private static FactSpec addFilterByMetaExists(
      @NonNull FactSpec spec, @NonNull FilterByMetaExists attribute) {
    return spec.metaExists(attribute.value());
  }

  private static FactSpec addFilterByMeta(@NonNull FactSpec spec, @NonNull FilterByMeta attribute) {
    return spec.meta(attribute.key(), attribute.value());
  }

  private static Collection<CallTarget> getRelevantClasses(@NonNull Projection p) {
    return getRelevantClasses(new CallTarget(getRelevantClass(p.getClass()), o -> o));
  }

  private static Collection<CallTarget> getRelevantClasses(@NonNull CallTarget root) {
    List<CallTarget> classes = new LinkedList<>();
    classes.add(root);
    Arrays.stream(root.clazz().getDeclaredClasses())
        .filter(c -> !Modifier.isStatic(c.getModifiers()))
        .forEach(
            c ->
                classes.addAll(
                    getRelevantClasses(
                        new CallTarget(c, p -> resolveTargetObject(root.resolver().apply(p), c)))));
    return classes;
  }

  @VisibleForTesting
  @SuppressWarnings({"java:S3011", "java:S1141", "ReassignedVariable"})
  static Object resolveTargetObject(Object parent, Class<?> c) {
    try {
      parent = ProjectorImpl.unwrapProxy(parent); // in case parent is wrapped by AOP

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
