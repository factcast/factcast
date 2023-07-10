package org.factcast.test;

import kotlin.jvm.internal.Intrinsics;
import org.factcast.core.Fact;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.UUID;

public class FactFromEvent {

    public static Fact factFromEvent(@NotNull EventObject event) {
        return factFromEvent(event, 0);
    }

    public static Fact factFromEvent(@NotNull EventObject event, long serial) {
        return factFromEvent(event, serial, UUID.randomUUID());
    }

    public static Fact factFromEvent(@NotNull EventObject event, long serial, UUID id) {
        Annotation[] annotations = event.getClass().getAnnotations();
        Specification specs = null;
        int i = 0;
        while (specs == null && i < annotations.length) {
            if (annotations[i] instanceof Specification) {
                specs = (Specification) annotations[i];
            }
            i++;
        }

        if (specs == null) {
            throw new IllegalStateException("invalid event object");
        } else {
            return Fact.builder()
                    .type(specs.type())
                    .ns(specs.ns())
                    .version(specs.version())
                    .id(id)
                    .meta("_ser", String.valueOf(serial))
                    .buildWithoutPayload();
        }
    }
}
