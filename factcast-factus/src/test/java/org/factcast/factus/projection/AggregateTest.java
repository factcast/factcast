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
package org.factcast.factus.projection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;

import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AggregateTest {

  @Test
  void hashOfId() {
    TestAggregate a = new TestAggregate();
    UUID id = spy(UUID.randomUUID());
    a.aggregateId(id);

    int result = a.hashCode();
    assertEquals(Objects.hash(id), result);
  }

  @Test
  void equals() {
    TestAggregate a = new TestAggregate();
    TestAggregate b = new TestAggregate();
    a.aggregateId(UUID.randomUUID());
    b.aggregateId(UUID.randomUUID());

    assertNotEquals(a, b);
    assertNotEquals(b, a);

    b.aggregateId(a.aggregateId());

    assertEquals(a, b);
    assertEquals(b, a);
  }

  class TestAggregate extends Aggregate {}
}
