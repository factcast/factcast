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
import org.factcast.core.FactHeader;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.spec.FactSpecCoordinates;
import org.factcast.highlevel.EventPojo;
import org.factcast.highlevel.Handler;
import org.factcast.highlevel.HandlerFor;
import org.factcast.highlevel.aggregate.ActivatableProjection;
import org.factcast.highlevel.aggregate.Aggregate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public class DefaultEventApplier<A extends ActivatableProjection> implements EventApplier<A> {

    private final ActivatableProjection projection;

    private static final Map<Class<? extends ActivatableProjection>, Map<FactSpecCoordinates, Dispatcher>> cache = new HashMap<>();

    interface TargetObjectResolver extends Function<ActivatableProjection, Object> {
    }

    interface ParameterTransformer extends Function<Fact, Object[]> {
    }

    private final Map<FactSpecCoordinates, Dispatcher> dispatchInfo;

    protected DefaultEventApplier(EventSerializer ctx, ActivatableProjection p) {
        this.projection = p;
        this.dispatchInfo = cache.computeIfAbsent(p.getClass(), c -> discoverDispatchInfo(ctx, p));
    }

    public void apply(@NonNull Fact f) {
        log.trace("Dispatching fact {}", f.id());
        val coords = FactSpecCoordinates.from(f);
        val dispatch = dispatchInfo.get(coords);
        if (dispatch == null) {
            throw new IllegalStateException("Unexpected Fact coordinates: '" + coords + "'");
        }

        try {
            dispatch.invoke(projection, f);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException
                | JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public List<FactSpec> createFactSpecs() {
        List<FactSpec> discovered = dispatchInfo.values()
                .stream()
                .map(d -> d.spec.copy())
                .collect(Collectors.toList());

        if (projection instanceof Aggregate) {
            UUID aggId = ((Aggregate) projection).id();
            for (FactSpec factSpec : discovered) {
                factSpec.aggId(aggId);
            }
        }

        val ret = projection.postprocess(discovered);
        if (ret == null || ret.isEmpty()) {
            throw new IllegalArgumentException("No FactSpecs discovered from " + projection
                    .getClass()
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

        void invoke(ActivatableProjection projection, Fact f)
                throws InvocationTargetException, IllegalAccessException,
                NoSuchMethodException, JsonProcessingException {
            dispatchMethod.invoke(objectResolver.apply(projection), parameterTransformer.apply(f));
        }
    }

    private static Map<FactSpecCoordinates, Dispatcher> discoverDispatchInfo(
            EventSerializer deserializer, ActivatableProjection p) {
        Map<FactSpecCoordinates, Dispatcher> map = new HashMap<>();

        Collection<CallTarget> relevantClasses = getRelevantClasses(p);
        relevantClasses.forEach(callTarget -> {
            Method[] methods = callTarget.clazz.getDeclaredMethods();
            Arrays.stream(methods)
                    .filter(DefaultEventApplier::isEventHandlerMethod)
                    .forEach(m -> {

                        FactSpec fs = discoverFactSpec(m);
                        FactSpecCoordinates key = FactSpecCoordinates.from(fs);

                        Dispatcher dispatcher = new Dispatcher(
                                m, callTarget.resolver,
                                createParameterTransformer(deserializer, m), fs, deserializer);
                        val before = map.put(key, dispatcher);
                        if (before != null) {
                            throw new UnsupportedOperationException(
                                    "Duplicate Handler method found for spec '" + key + "':\n " + m
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

        HandlerFor handlerFor = m.getAnnotation(HandlerFor.class);
        if (handlerFor != null) {
            return FactSpec.ns(
                    handlerFor.ns())
                    .type(
                            handlerFor.type())
                    .version(
                            handlerFor.version());
        }

        List<Class<?>> eventPojoTypes = Arrays.stream(m.getParameterTypes())
                .filter(t -> EventPojo.class.isAssignableFrom(t))
                .collect(Collectors.toList());

        if (eventPojoTypes.isEmpty()) {
            throw new IllegalArgumentException("Cannot introspect FactSpec from " + m
                    + ". Either use @HandlerFor or pass an EventPojo as a parameter.");
        } else {
            if (eventPojoTypes.size() > 1) {
                throw new IllegalArgumentException(
                        "Multiple EventPojo Parameters. Cannot introspect FactSpec from " + m);
            } else {
                Class<?> eventPojoType = eventPojoTypes.get(0);
                return FactSpec.from(eventPojoType);
            }
        }
    }

    private static ParameterTransformer createParameterTransformer(
            EventSerializer ctx,
            Method m) {

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
    private static Function<Fact, Object> createSingleParameterTransformer(Method m,
            EventSerializer deserializer, Class<?> type) {
        if (EventPojo.class.isAssignableFrom(type)) {
            return p -> deserializer.deserialize((Class<? extends EventPojo>) type, p
                    .jsonPayload());
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

        throw new UnsupportedOperationException("Don't know how resolve " + type
                + " from a Fact for a parameter to method:\n " + m);

    }

    @Value
    static class CallTarget {
        Class<?> clazz;

        TargetObjectResolver resolver;
    }

    private static Collection<CallTarget> getRelevantClasses(ActivatableProjection p) {
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
            throw new IllegalStateException("Cannot instantiate " + c, e);
        }
    }

    private static boolean isEventHandlerMethod(Method m) {
        if (m.getAnnotation(Handler.class) != null || m.getAnnotation(HandlerFor.class) != null) {

            if (!m.getReturnType().equals(void.class)) {
                throw new UnsupportedOperationException("Handler methods must return void, but \n "
                        + m + "\n returns '" + m.getReturnType() + "'");
            }

            if (m.getParameterCount() == 0) {
                throw new UnsupportedOperationException(
                        "Handler methods must have at least one parameter: " + m);
            }

            if (Modifier.isPublic(m.getModifiers())) {
                log.warn("Handler methods should not be public: " + m);
            }

            for (Class<?> type : m.getParameterTypes()) {
                // trigger transformer creation in order to fail fast
                createSingleParameterTransformer(m, null, type);
            }

            return true;
        }
        return false;
    }

}
