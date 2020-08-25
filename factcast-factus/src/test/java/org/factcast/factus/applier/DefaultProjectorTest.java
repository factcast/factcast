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
package org.factcast.factus.applier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.assertj.core.util.Maps;
import org.factcast.core.Fact;
import org.factcast.core.event.EventConverter;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.event.DefaultEventSerializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Blackbox test, we are wiring real objects into the test class, no mocks.
 */
class DefaultProjectorTest {

    private final DefaultEventSerializer eventSerializer = new DefaultEventSerializer(FactCastJson
            .mapper());

    private EventConverter eventConverter = new EventConverter(eventSerializer);

    @Nested
    class WhenApplying {

        @Test
        void applySimple() {
            // INIT
            UUID aggregateId = UUID.randomUUID();
            SimpleEvent event = new SimpleEvent(Maps.newHashMap("Some key", "Some value"),
                    aggregateId, "abc");

            Fact fact = eventConverter.toFact(event);

            SimpleProjection projection = new SimpleProjection();

            DefaultProjector<SimpleProjection> underTest = new DefaultProjector<>(eventSerializer,
                    projection);

            // RUN
            underTest.apply(fact);

            // ASSERT
            assertThat(projection.recordedEvent())
                    .isEqualTo(event);

        }

        @Test
        void applyWithSubclass() {
            // INIT
            UUID aggregateId = UUID.randomUUID();
            UUID aggregateId2 = UUID.randomUUID();

            ComplexEvent event = new ComplexEvent(Maps.newHashMap("Some key", "Some value"),
                    aggregateId, "abc");
            ComplexEvent2 event2 = new ComplexEvent2(Maps.newHashMap("Some key",
                    "Some other value"),
                    aggregateId2, "def");

            Fact fact = eventConverter.toFact(event);
            Fact fact2 = eventConverter.toFact(event2);

            ComplexProjection projection = new ComplexProjection();

            DefaultProjector<ComplexProjection> underTest = new DefaultProjector<>(eventSerializer,
                    projection);

            // RUN
            underTest.apply(fact);
            underTest.apply(fact2);

            // ASSERT
            assertThat(projection.recordedEvent())
                    .isEqualTo(event);

            assertThat(projection.recordedEvent2())
                    .isEqualTo(event2);

        }
    }
}