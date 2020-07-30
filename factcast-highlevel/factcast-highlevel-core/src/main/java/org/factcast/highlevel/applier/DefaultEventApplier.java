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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
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
    final EventApplierContext ctx;

    private final List<FactSpec> factSpecs = new LinkedList<>();

    private final Projection projection;

    @Value
    @VisibleForTesting
    class DispatchInfo {

        Class<? extends EventPojo> eventClass;

        Object targetObject;

        Method dispatchMethod;

        @SuppressWarnings("unchecked")
        <T extends EventPojo> T fromFact(Fact f) throws NoSuchMethodException,
                InvocationTargetException, IllegalAccessException, JsonProcessingException {
            return (T) ctx.mapper().readerFor(eventClass).readValue(f.jsonPayload());
        }

        void invoke(Fact f) throws InvocationTargetException, IllegalAccessException,
                NoSuchMethodException, JsonProcessingException {
            dispatchMethod.invoke(targetObject, fromFact(f));
        }
    }

    private final Map<String, DispatchInfo> eventClassMap;

    protected DefaultEventApplier(EventApplierContext ctx, Projection p) {
        this.ctx = ctx;
        this.projection = p;
        this.eventClassMap = buildEventClassMap();
    }

    private Map<String, DispatchInfo> buildEventClassMap() {
        Map<String, DispatchInfo> map = new HashMap<>();

        getRelevantClasses().forEach(target -> Arrays.stream(target.clazz().getDeclaredMethods())
                .filter(this::isEventHandlerMethod)
                .forEach(m -> {
                    val paramType = getParameterType(m);
                    val factType = getFactType(paramType);

                    val before = map.put(factType, new DispatchInfo(
                            (Class<? extends EventPojo>) paramType, target.lazyInstanceSupplier
                                    .get(), m));
                    if (before != null) {
                        throw new UnsupportedOperationException(
                                "Duplicate @Handler found for type '" + paramType + "':\n " + m
                                        + "\n clashes with\n " + before.dispatchMethod());
                    }

                    log.debug("Discovered Event handling method " + m.toString());
                    m.setAccessible(true);
                    factSpecs.add(FactSpec.from(paramType));

                }));

        return map;
    }

    @Value
    static class CallTarget {
        Class<?> clazz;

        Supplier<Object> lazyInstanceSupplier;
    }

    private Collection<CallTarget> getRelevantClasses() {
        return getRelevantClasses(new CallTarget(projection.getClass(), () -> projection));
    }

    private Collection<CallTarget> getRelevantClasses(CallTarget root) {
        List<CallTarget> classes = new LinkedList<>();
        classes.add(root);
        Arrays.asList(root.clazz().getDeclaredClasses())
                .forEach(c -> classes.addAll(getRelevantClasses(new CallTarget(c,
                        () -> resolveTargetObject(root.lazyInstanceSupplier.get(), c)))));
        return classes;
    }

    private Object resolveTargetObject(Object parent, Class<?> c) {
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

    private boolean isEventHandlerMethod(Method m) {
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

    private String getFactType(Class<?> parameterType) {
        Specification s = parameterType.getAnnotation(Specification.class);
        String annotatedType = s.type();
        if (annotatedType != null && !annotatedType.trim().isEmpty()) {
            return annotatedType;
        } else {
            return parameterType.getSimpleName();
        }
    }

    private Class<?> getParameterType(Method m) {
        return m.getParameterTypes()[0];
    }

    public void apply(@NonNull Fact f) {
        log.trace("Dispatching fact {}", f.jsonHeader());
        val type = f.type();
        val dispatch = eventClassMap.get(type);
        if (dispatch == null) {
            throw new IllegalStateException("Unexpected Fact type: '" + type + "'");
        }

        try {
            dispatch.invoke(f);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException
                | JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<FactSpec> createFactSpecs() {
        if (factSpecs.isEmpty()) {
            throw new IllegalArgumentException("No handler methods found on " + this.getClass()
                    .getCanonicalName());
        }
        return factSpecs.stream().map(fs -> fs.copy()).collect(Collectors.toList());
    }
}
