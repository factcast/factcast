package org.factcast.factus.projection;

import org.factcast.factus.event.EventObject;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.util.*;

/**
 * using default type is only allowed on Method
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Repeatable(OverrideNamespaces.class)
public @interface OverrideNamespace {
    Class<? extends EventObject> DISCOVER = DiscoverFromMethodParameter.class;

    String value();

    Class<? extends EventObject> type() default DiscoverFromMethodParameter.class;

    class DiscoverFromMethodParameter implements EventObject {
        @Override
        public Set<UUID> aggregateIds() {
            return Collections.emptySet();
        }
    }

    ;
}
