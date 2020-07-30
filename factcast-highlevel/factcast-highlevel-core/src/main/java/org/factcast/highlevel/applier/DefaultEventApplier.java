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
package org.factcast.highlevel.applier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.spec.FactSpecCoordinates;
import org.factcast.core.spec.Specification;
import org.factcast.highlevel.EventPojo;
import org.factcast.highlevel.Handler;
import org.factcast.highlevel.aggregate.Projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * TODO - additional factHeader parameter - optional fact&specification -
 * abstract out OM, to enable use of gson or similar - discovery cache per class
 */
@RequiredArgsConstructor
@Slf4j
public class DefaultEventApplier<A extends Projection> implements EventApplier<A> {
    private final EventApplierContext ctx;

    private final Projection projection;

    private static final Map<Class<? extends Projection>, Map<FactSpecCoordinates, Dispatcher>> cache = new HashMap<>();

    interface TargetObjectResolver extends Function<Projection, Object> {
    }

    interface ParameterTransformer extends Function<Fact, Object[]> {
    }

    private final Map<FactSpecCoordinates, Dispatcher> dispatchInfo;

    protected DefaultEventApplier(EventApplierContext ctx, Projection p) {
        this.ctx = ctx;
        this.projection = p;
        this.dispatchInfo = cache.computeIfAbsent(p.getClass(), c -> discoverDispatchInfo(ctx, p));
    }

    public void apply(@NonNull Fact f) {
        log.trace("Dispatching fact {}", f.jsonHeader());
        val coords = FactSpecCoordinates.from(f);
        val dispatch = dispatchInfo.get(coords);
        if (dispatch == null) {
            throw new IllegalStateException("Unexpected Fact coordinates: '" + coords + "'");
        }

        try {
            dispatch.invoke(ctx, projection, f);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException
                | JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<FactSpec> createFactSpecs() {
        if (dispatchInfo.isEmpty()) {
            throw new IllegalArgumentException("No handler methods found on " + projection
                    .getClass()
                    .getCanonicalName());
        }
        return dispatchInfo.values().stream().map(d -> d.spec.copy()).collect(Collectors.toList());
    }

    // --------------------------------------------------------
    @Value
    @VisibleForTesting
    static class Dispatcher {

        Method dispatchMethod;

        TargetObjectResolver objectResolver;

        ParameterTransformer parameterTransformer;

        FactSpec spec;

        void invoke(EventApplierContext ctx, Projection projection, Fact f)
                throws InvocationTargetException, IllegalAccessException,
                NoSuchMethodException, JsonProcessingException {
            dispatchMethod.invoke(objectResolver.apply(projection), parameterTransformer.apply(f));
        }
    }

    private static Map<FactSpecCoordinates, Dispatcher> discoverDispatchInfo(
            EventApplierContext ctx, Projection p) {
        Map<FactSpecCoordinates, Dispatcher> map = new HashMap<>();

        Collection<CallTarget> relevantClasses = getRelevantClasses(p);
        relevantClasses.forEach(callTarget -> {
            Method[] methods = callTarget.clazz.getDeclaredMethods();
            Arrays.stream(methods)
                    .filter(DefaultEventApplier::isEventHandlerMethod)
                    .forEach(m -> {

                        FactSpec fs = discoverFactSpec(m);

                        Dispatcher dispatcher = new Dispatcher(m, callTarget.resolver,
                                createParameterTransformer(ctx, m), fs);
                        val before = map.put(FactSpecCoordinates.from(fs), dispatcher);
                        if (before != null) {
                            throw new UnsupportedOperationException(
                                    "Duplicate @Handler found for spec '" + fs + "':\n " + m
                                            + "\n clashes with\n " + before.dispatchMethod());
                        }

                        log.debug("Discovered Event handling method " + m.toString());
                        m.setAccessible(true);

                    });
        });

        if (map.isEmpty()) {
            throw new IllegalArgumentException("No handler methods discovered on " + p.getClass());
        }

        return map;
    }

    private static FactSpec discoverFactSpec(Method m) {
        List<Class<?>> eventPojoTypes = Arrays.stream(m.getParameterTypes())
                .filter(t -> EventPojo.class.isAssignableFrom(t))
                .collect(Collectors.toList());

        if (eventPojoTypes.isEmpty()) {
            // TODO add @Spec
            throw new IllegalArgumentException("Cannot introspect FactSpec from " + m);
        } else {
            if (eventPojoTypes.size() > 1) {
                throw new IllegalArgumentException(
                        "Multiple EventPojo Parameters. Cannot introspect FactSpec from " + m);
            } else {
                return FactSpec.from(eventPojoTypes.get(0));
            }
        }
    }

    private static ParameterTransformer createParameterTransformer(EventApplierContext ctx,
            Method m) {

        // TODO additional params, sanitize
        Class<?> parameterType = m.getParameterTypes()[0];
        return p -> {
            try {
                return new Object[] { ctx.mapper()
                        .readerFor(parameterType)
                        .readValue(p.jsonPayload()) };
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    @Value
    static class CallTarget {
        Class<?> clazz;

        TargetObjectResolver resolver;
    }

    private static Collection<CallTarget> getRelevantClasses(Projection p) {
        return getRelevantClasses(new CallTarget(p.getClass(), o -> o));
    }

    private static Collection<CallTarget> getRelevantClasses(CallTarget root) {
        List<CallTarget> classes = new LinkedList<>();
        classes.add(root);
        Arrays.asList(root.clazz().getDeclaredClasses())
                .forEach(c -> classes.addAll(getRelevantClasses(new CallTarget(c,
                        p -> resolveTargetObject(root.resolver.apply(p), c)))));
        return classes;
    }

    private static Object resolveTargetObject(Object parent, Class<?> c) {
        try {
            Constructor<?> ctor = null;
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

        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException
                | InvocationTargetException e) {
            throw new UnsupportedOperationException("Cannot instantiate " + c, e);
        }
    }

    private static boolean isEventHandlerMethod(Method m) {
        if (m.getAnnotation(Handler.class) != null) {

            if (m.getParameterCount() != 1) {
                throw new UnsupportedOperationException(
                        "Methods annotated with @" + Handler.class.getSimpleName()
                                + " need to have one parameter only: " + m);
            }

            if (!EventPojo.class.isAssignableFrom(getParameterType(m))) {
                throw new UnsupportedOperationException("Methods annotated with @" + Handler.class
                        .getSimpleName()
                        + " need to have one parameter extending Type " + EventPojo.class
                                .getSimpleName() + ": " + m);
            }

            if (!m.getReturnType().equals(void.class)) {
                throw new UnsupportedOperationException("Methods annotated with @" + Handler.class
                        .getSimpleName()
                        + " need to return void, but returns '" + m.getReturnType() + "': " + m);
            }

            if (Modifier.isPublic(m.getModifiers())) {
                log.warn("Methods annotated with @" + Handler.class.getSimpleName()
                        + " should not be public: " + m);
            }

            if (!m.getReturnType().equals(void.class)) {
                throw new UnsupportedOperationException("Methods annotated with @" + Handler.class
                        .getSimpleName()
                        + " needs to return void, but returns '" + m.getReturnType() + "': " + m);
            }

            return true;

        }
        return false;
    }

    private static String getFactType(Class<?> parameterType) {
        Specification s = parameterType.getAnnotation(Specification.class);
        String annotatedType = s.type();
        if (annotatedType != null && !annotatedType.trim().isEmpty()) {
            return annotatedType;
        } else {
            return parameterType.getSimpleName();
        }
    }

    private static Class<?> getParameterType(Method m) {
        return m.getParameterTypes()[0];
    }

}
