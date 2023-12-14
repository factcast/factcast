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
package org.factcast.factus;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)

// TODO
class ProjectionAccessorTest {

  //
  //  class Agg extends Aggregate {}
  //
  //  private final ProjectionAccessor underTest =
  //      new ProjectionAccessor() {
  //        @Override
  //        public <P extends SnapshotProjection> @NonNull P fetch(@NonNull Class<P>
  // projectionClass) {
  //          return null;
  //        }
  //
  //        @Override
  //        public @NonNull <A extends Aggregate> Optional<A> find(
  //            @NonNull Class<A> aggregateClass, @NonNull UUID aggregateId) {
  //          return Optional.empty();
  //        }
  //
  //        @Override
  //        public <P extends ManagedProjection> void update(
  //            @NonNull P managedProjection, @NonNull Duration maxWaitTime) throws TimeoutException
  // {}
  //      };
  //
  //  @Nested
  //  class WhenFetching {
  //    private final UUID AGGREGATE_ID = UUID.randomUUID();
  //
  //    @BeforeEach
  //    void setup() {}
  //
  //    @Test
  //    void throwsOnNonExistentAggregate() {
  //      assertThatThrownBy(() -> underTest.fetch(Agg.class, AGGREGATE_ID))
  //          .isInstanceOf(IllegalStateException.class);
  //    }
  //  }
}
