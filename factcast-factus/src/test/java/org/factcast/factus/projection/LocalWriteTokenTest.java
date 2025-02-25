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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class LocalWriteTokenTest {

  private final LocalWriteToken underTest = new LocalWriteToken();

  @Test
  void acquireWriteToken() throws Exception {
    // acquire lock
    WriterToken lock = underTest.acquireWriteToken(Duration.ofSeconds(1));

    // we have a lock when this is not null
    assertThat(lock).isNotNull();
    assertTrue(lock.isValid());

    // test that we can unlock without exception
    lock.close();
    assertFalse(lock.isValid());
  }


  @Test
  void cannotLockTwice() throws Exception {
    WriterToken first = underTest.acquireWriteToken(Duration.ofSeconds(1));
    assertThat(first).isNotNull();
    WriterToken second = underTest.acquireWriteToken(Duration.ofSeconds(1));
    Assertions.assertThat(second).isNull();

    first.close();
  }

  @Test
  void blockedAcquire() throws Exception {
    WriterToken first = underTest.acquireWriteToken(Duration.ofSeconds(1));
    CountDownLatch cl = new CountDownLatch(1);
    CompletableFuture<WriterToken> thirdAttempt =
        CompletableFuture.supplyAsync(
            () -> {
              cl.countDown();
              return underTest.acquireWriteToken(Duration.ofDays(1));
            });
    cl.await();
    first.close();

    WriterToken successful = thirdAttempt.get(2, TimeUnit.SECONDS);
    Assertions.assertThat(successful).isNotNull();
    successful.close();
  }
}
