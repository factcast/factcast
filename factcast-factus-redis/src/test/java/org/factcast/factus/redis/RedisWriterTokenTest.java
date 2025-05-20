/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.factus.redis;

import static org.mockito.Mockito.*;

import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("resource")
class RedisWriterTokenTest {

  private static final String LOCKNAME = "dalock";
  @Mock private @NonNull RLock lock;
  @Mock private @NonNull RFencedLock flock;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private RedissonClient redisson;

  @Nested
  class WhenConstructingWithFencedLock {
    @Test
    void tokenIsMemorized() {
      when(flock.isLocked()).thenReturn(true);
      when(flock.getToken()).thenReturn(1L);
      RedisWriterToken uut = new RedisWriterToken(flock);
      verify(flock).getToken();
      Assertions.assertThat(uut.token()).isEqualTo(1L);
    }

    @Test
    void closesIfOwned() {
      when(flock.isLocked()).thenReturn(true);
      when(flock.getToken()).thenReturn(1L);
      new RedisWriterToken(flock).close();
      verify(flock).forceUnlock();
    }

    @Test
    void doesNotCloseIfNotOwned() {
      when(flock.isLocked()).thenReturn(true);
      when(flock.getToken()).thenReturn(1L, 2L);
      new RedisWriterToken(flock).close();
      verify(flock, never()).forceUnlock();
    }

    @Test
    void doesNotCloseIfNotLocked() {
      when(flock.isLocked()).thenReturn(true, false);
      when(flock.getToken()).thenReturn(1L);
      new RedisWriterToken(flock).close();
      verify(flock, never()).forceUnlock();
    }
  }

  @Nested
  class WhenCheckingIfIsValid {
    @Test
    void testIsValid() {
      when(flock.isLocked()).thenReturn(true);
      when(flock.getToken()).thenReturn(1L);
      final RedisWriterToken uut = new RedisWriterToken(flock);
      // initial call
      Assertions.assertThat(uut.isValid()).isTrue();

      verify(flock).getToken();
      verify(flock).isLocked();

      Mockito.reset(flock);
      when(flock.isLocked()).thenReturn(true);
      when(flock.getToken()).thenReturn(1L);

      // another 5 calls within the cache period
      for (int i = 0; i < 5; i++) {
        Assertions.assertThat(uut.isValid()).isTrue();
      }

      verify(flock, never()).getToken();
      verify(flock, never()).isLocked();

      // simulate time passed
      uut.liveness().set(System.currentTimeMillis() - 1000000);

      // another 5 calls, first will refresh
      for (int i = 0; i < 5; i++) {
        Assertions.assertThat(uut.isValid()).isTrue();
      }
      verify(flock).getToken();
      verify(flock).isLocked();
    }

    @Test
    void notValidIfAlreadyClosed() {
      when(flock.isLocked()).thenReturn(true);
      when(flock.getToken()).thenReturn(1L, 2L);
      final RedisWriterToken uut = new RedisWriterToken(flock);
      // initial call, should close
      uut.liveness().set(System.currentTimeMillis() - 1000000);
      Assertions.assertThat(uut.isValid()).isFalse();

      // subsequent calls should exit early
      Assertions.assertThat(uut.isValid()).isFalse();
      Assertions.assertThat(uut.isValid()).isFalse();
      Assertions.assertThat(uut.isValid()).isFalse();
      // due to
      Assertions.assertThat(uut.alreadyClosed()).isTrue();
    }
  }
}
