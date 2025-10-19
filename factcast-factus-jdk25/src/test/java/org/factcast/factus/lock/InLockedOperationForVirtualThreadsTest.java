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
package org.factcast.factus.lock;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InLockedOperationForVirtualThreadsTest {

  @InjectMocks private InLockedOperationForVirtualThreads underTest;

  @Test
  void initialIsFalse() {
    underTest.assertNotInLockedOperation();
  }

  @Test
  void failsIfLocked() {
    assertThrows(
        IllegalStateException.class,
        () -> underTest.runLocked(underTest::assertNotInLockedOperation));
  }

  @Test
  void doesNotFailIfLockedOnOtherThread() throws Exception {
    CountDownLatch wait = new CountDownLatch(1);
    CountDownLatch arrived = new CountDownLatch(1);

    new Thread(
            () ->
                underTest.runLocked(
                    () -> {
                      try {
                        assertThatThrownBy(() -> underTest.assertNotInLockedOperation())
                            .isInstanceOf(IllegalStateException.class);

                        arrived.countDown();
                        wait.await();

                        // still locked!
                        assertThatThrownBy(() -> underTest.assertNotInLockedOperation())
                            .isInstanceOf(IllegalStateException.class);

                      } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                      }
                    }))
        .start();

    arrived.await();

    // not blocked by other thread's lock
    underTest.assertNotInLockedOperation();

    // lock and release for this thread
    assertThrows(
        IllegalStateException.class,
        () -> underTest.runLocked(underTest::assertNotInLockedOperation));

    // release thread
    wait.countDown();
  }

  @Test
  void doesNotFailIfUnLocked() {
    underTest.runLocked(() -> {});

    underTest.assertNotInLockedOperation();
  }
}
