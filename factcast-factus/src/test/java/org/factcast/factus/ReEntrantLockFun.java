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
package org.factcast.factus;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.*;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.factcast.factus.projection.*;
import org.junit.jupiter.api.Test;

class ReEntrantLockFun {

  Lock rl = new ReentrantLock();

  @Test
  void simpleUnlock() {
    rl.lock();
    rl.unlock();
  }

  @SneakyThrows
  @Test
  void unlockFromOtherThread() {
    Assertions.assertThatThrownBy(
            () -> {
              rl.lock();
              CompletableFuture.runAsync(
                      () -> {
                        rl.unlock();
                      })
                  .get();
            })
        .hasCauseInstanceOf(IllegalMonitorStateException.class);
  }

  LocalWriteToken wt = new LocalWriteToken();

  @SneakyThrows
  @Test
  void wtUnlockFromOtherThread() {
    org.junit.jupiter.api.Assertions.assertDoesNotThrow(
        () -> {
          WriterToken t = this.wt.acquireWriteToken(Duration.ofMillis(100));
          Assertions.assertThat(t).isNotNull();

          CompletableFuture.runAsync(
                  () -> {
                    try {
                      t.close();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  })
              .get();
        });
  }
}
