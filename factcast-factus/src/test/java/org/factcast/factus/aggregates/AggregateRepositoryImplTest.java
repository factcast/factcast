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
package org.factcast.factus.aggregates;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import org.factcast.factus.Factus;
import org.factcast.factus.projection.Aggregate;
import org.junit.jupiter.api.Test;

class AggregateRepositoryImplTest {

  // simple aggregate and id types for generics
  static class MyAgg extends Aggregate {}

  static class MyId implements AggregateIdentifier {
    final UUID id;

    MyId(UUID id) {
      this.id = id;
    }

    @Override
    public @NonNull UUID getId() {
      return id;
    }
  }

  @Test
  void find_delegatesToFactus_andReturnsResult_present() {
    // arrange
    Factus factus = mock(Factus.class);
    AggregateRepositoryImpl<MyId, MyAgg> underTest =
        new AggregateRepositoryImpl<>(MyAgg.class, factus);

    UUID id = UUID.randomUUID();
    MyId myId = new MyId(id);
    MyAgg a = new MyAgg();
    when(factus.find(eq(MyAgg.class), eq(id))).thenReturn(Optional.of(a));

    // act
    Optional<MyAgg> res = underTest.find(myId);

    // assert
    assertThat(res).containsSame(a);
    verify(factus).find(MyAgg.class, id);
    verifyNoMoreInteractions(factus);
  }

  @Test
  void find_delegatesToFactus_andReturnsEmpty() {
    // arrange
    Factus factus = mock(Factus.class);
    AggregateRepositoryImpl<MyId, MyAgg> underTest =
        new AggregateRepositoryImpl<>(MyAgg.class, factus);

    UUID id = UUID.randomUUID();
    MyId myId = new MyId(id);
    when(factus.find(eq(MyAgg.class), eq(id))).thenReturn(Optional.empty());

    // act
    Optional<MyAgg> res = underTest.find(myId);

    // assert
    assertThat(res).isEmpty();
    verify(factus).find(MyAgg.class, id);
    verifyNoMoreInteractions(factus);
  }
}
