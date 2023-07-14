/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.test;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.DefaultFact;
import org.factcast.core.Fact;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.CollectionUtils;

public class FactFromEvent {

    static private final ObjectMapper MAPPER = new ObjectMapper();

    public static Builder factFromEvent(@NotNull EventObject event) {
        return factFromEvent(event, 0);
    }

    public static Builder factFromEvent(@NotNull EventObject event, long serial) {
        return factFromEvent(event, serial, UUID.randomUUID());
    }

    @SneakyThrows
    public static Builder factFromEvent(@NotNull EventObject event, long serial, UUID id) {
        Specification specs = event.getClass().getAnnotation(Specification.class);

        if (specs == null) {
            throw new IllegalArgumentException("invalid event object");
        } else {
            Builder builder = FactFromEvent.builder()
                    .serial(serial)
                    .type(specs.type())
                    .ns(specs.ns())
                    .version(specs.version())
                    .id(id)
                    .payload(MAPPER.writeValueAsString(event));

            if (!CollectionUtils.isEmpty(event.aggregateIds())) {
                event.aggregateIds().forEach(builder::aggId);
            }

            return builder;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends Fact.Builder {
        private String payload;
        public Builder serial(long id) {
            meta("_ser", String.valueOf(id));
            return this;
        }

        public Builder aggId(@NonNull UUID aggId) {
            super.aggId(aggId);
            return this;
        }

        public Builder ns(@NonNull String ns) {
            super.ns(ns);
            return this;
        }

        public Builder id(@NonNull UUID id) {
            super.id(id);
            return this;
        }

        public Builder type(@NonNull String type) {
            super.type(type);
            return this;
        }

        public Builder version(int version) {
            super.version(version);
            return this;
        }

        public Builder meta(@NonNull String key, String value) {
            super.meta(key, value);
            return this;
        }

        public Builder payload(@NonNull String payload) {
            this.payload = payload;
            return this;
        }

        public Fact build() {
            return super.build(payload);
        }
    }
}
