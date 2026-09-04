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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class AggregateIdPropertyFilterTest {

  /** deliberately not made accessible, so reading it from another class fails */
  @SuppressWarnings("unused")
  private UUID inaccessible;

  @SneakyThrows
  private static List<Field> accessibleChain(String fieldName) {
    Field field = FilterByAggIdPropertyEvent.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return List.of(field);
  }

  @SneakyThrows
  @Test
  void rejectsNonAggregateProjection() {
    AggregateIdPropertyFilter uut =
        new AggregateIdPropertyFilter(accessibleChain("recommendedUserId"), 0);
    FilterByAggIdPropertyEvent event =
        new FilterByAggIdPropertyEvent(UUID.randomUUID(), UUID.randomUUID());

    assertThatThrownBy(() -> uut.matches(new SimpleProjection(), new Object[] {event}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SimpleProjection.class.getName());
  }

  @SneakyThrows
  @Test
  void wrapsIllegalAccessException() {
    Field notAccessible = AggregateIdPropertyFilterTest.class.getDeclaredField("inaccessible");
    AggregateIdPropertyFilter uut = new AggregateIdPropertyFilter(List.of(notAccessible), 0);

    UUID aggregateId = UUID.randomUUID();
    FilterByAggIdPropertyAggregate aggregate = new FilterByAggIdPropertyAggregate(aggregateId);
    FilterByAggIdPropertyEvent event =
        new FilterByAggIdPropertyEvent(aggregateId, UUID.randomUUID());

    assertThatThrownBy(() -> uut.matches(aggregate, new Object[] {event}))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(IllegalAccessException.class);
  }
}
