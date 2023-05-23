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
package org.factcast.core.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import lombok.SneakyThrows;
import org.factcast.core.store.FactStore;
import org.junit.jupiter.api.Test;

class LockedOperationBuilderTest {

  final DeprecatedLockedOperationBuilder uut =
      new DeprecatedLockedOperationBuilder(mock(FactStore.class), "ns");

  @Test
  void testAttemptAbortsOnNull() {
    assertThrows(
        AttemptAbortedException.class, () -> uut.on(UUID.randomUUID()).attempt(() -> null));
  }

  @Test
  void testAttemptWithoutPublishing() {
    UUID aggId = UUID.randomUUID();
    LockedOperationBuilder on = uut.on(aggId);
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          on.attempt(() -> mock(IntermediatePublishResult.class));
        });
  }

  @SneakyThrows
  @Test
  void testAttemptWithoutPublishingOnRequest() {
    UUID aggId = UUID.randomUUID();
    LockedOperationBuilder on = uut.on(aggId);
    // must not throw
    on.attempt(() -> Attempt.publishUnlessEmpty(Collections.emptyList()));
  }

  @SneakyThrows
  @Test
  void testAttemptWithoutPublishingButExecuteAndThen() {
    UUID aggId = UUID.randomUUID();
    LockedOperationBuilder on = uut.on(aggId);
    CountDownLatch cl = new CountDownLatch(1);

    on.attempt(() -> Attempt.withoutPublication().andThen(cl::countDown));

    assertThat(cl.await(1, TimeUnit.SECONDS)).isTrue();
  }
}
