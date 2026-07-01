/*
 * Copyright © 2017-2026 factcast.org
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import java.time.Duration;
import java.util.Optional;
import lombok.SneakyThrows;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.factcast.factus.projection.WriterToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoDbWriterTokenManagerTest {

  private static final String KEY = "key";

  @Mock private LockProvider lockProvider;

  MongoDbWriterTokenManager uut;

  @BeforeEach
  void setUp() {
    uut = new MongoDbWriterTokenManager(lockProvider, KEY);
  }

  @Nested
  class WhenAcquiringLock {

    private final ArgumentCaptor<LockConfiguration> captor =
        ArgumentCaptor.forClass(LockConfiguration.class);

    @Test
    @SneakyThrows
    void returnsTokenWhenLockingSuccessful() {
      Duration maxWaitDuration = Duration.ofSeconds(60L);
      SimpleLock lock = mock(SimpleLock.class);
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));

      final WriterToken res = uut.acquireWriteToken(maxWaitDuration);

      verify(lockProvider).lock(captor.capture());
      final LockConfiguration lockConfig = captor.getValue();
      assertThat(lockConfig.getName()).isEqualTo(KEY + "_lock");
      assertThat(lockConfig.getLockAtLeastFor()).isEqualTo(Duration.ofSeconds(1L));
      assertThat(lockConfig.getLockAtMostFor()).isEqualTo(maxWaitDuration);

      assertThat(res).isNotNull();
    }

    @Test
    @SneakyThrows
    void returnsLockWhenSuccessfulAfterMultipleAttempts() {
      Duration maxWaitDuration = Duration.ofSeconds(7);
      SimpleLock lock = mock(SimpleLock.class);
      when(lockProvider.lock(any(LockConfiguration.class)))
          .thenReturn(Optional.empty())
          .thenReturn(Optional.empty())
          .thenReturn(Optional.of(lock));

      final WriterToken res = uut.acquireWriteToken(maxWaitDuration);

      verify(lockProvider, times(3)).lock(captor.capture());
      final LockConfiguration lockConfig = captor.getValue();
      assertThat(lockConfig.getName()).isEqualTo(KEY + "_lock");
      assertThat(lockConfig.getLockAtLeastFor())
          .isEqualTo(MongoDbWriterTokenManager.MIN_LEASE_DURATION_SECONDS);
      assertThat(lockConfig.getLockAtMostFor())
          .isEqualTo(MongoDbWriterTokenManager.MAX_LEASE_DURATION_SECONDS);

      assertThat(res).isNotNull();
    }

    @Test
    @SneakyThrows
    void returnsNullIfLockCouldNotBeObtainedAfterMultipleAttempts() {
      Duration maxWaitDuration = Duration.ofSeconds(1);
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

      final WriterToken res = uut.acquireWriteToken(maxWaitDuration);

      // After 3. attempt, we waited 1,5 seconds and abort.
      verify(lockProvider, times(2)).lock(captor.capture());
      assertThat(res).isNull();
    }
  }
}
