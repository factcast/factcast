package org.factcast.factus.projection;

import java.lang.annotation.*;

/**
 * type must be non-default and unique
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface OverrideNamespaces {
    OverrideNamespace[] value();
}
