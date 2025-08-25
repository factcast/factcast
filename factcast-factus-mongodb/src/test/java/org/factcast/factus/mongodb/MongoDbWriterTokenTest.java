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
package org.factcast.factus.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoDbWriterTokenTest {

  @Mock SimpleLock lock;
  @Mock LockConfiguration lockConfiguration;
  @InjectMocks MongoDbWriterToken uut;

  @Test
  @DisplayName("isValid should return true when lock can be extended")
  void testIsValid() {
    when(lock.extend(any(Duration.class), any(Duration.class)))
        .thenReturn(Optional.of(mock(SimpleLock.class)));
    final Duration minDuration = Duration.ofSeconds(1L);
    final Duration maxDuration = Duration.ofSeconds(1L);
    when(lockConfiguration.getLockAtLeastFor()).thenReturn(minDuration);
    when(lockConfiguration.getLockAtMostFor()).thenReturn(maxDuration);

    assertThat(uut.isValid()).isTrue();

    verify(lock).extend(maxDuration, minDuration);
  }

  @Test
  @DisplayName("isValid should return false attempt to extend lock fails")
  void testIsValid_fails() {
    when(lock.extend(any(Duration.class), any(Duration.class)))
        .thenThrow(IllegalStateException.class);
    final Duration minDuration = Duration.ofSeconds(1L);
    final Duration maxDuration = Duration.ofSeconds(1L);
    when(lockConfiguration.getLockAtLeastFor()).thenReturn(minDuration);
    when(lockConfiguration.getLockAtMostFor()).thenReturn(maxDuration);

    assertThat(uut.isValid()).isFalse();

    verify(lock).extend(maxDuration, minDuration);
  }

  @Test
  @DisplayName("close calls unlock")
  void closeSuccessfully() {
    uut.close();

    verify(lock).unlock();
  }

  @Test
  @DisplayName("close catches the exception when unlock is unsuccessful")
  void closeCatchesExceptionWhenFailing() {
    doThrow(new IllegalStateException()).when(lock).unlock();

    assertThatCode(() -> uut.close()).doesNotThrowAnyException();

    verify(lock).unlock();
  }
}
