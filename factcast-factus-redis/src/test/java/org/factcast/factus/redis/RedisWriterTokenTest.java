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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Timer;
import java.util.TimerTask;
import lombok.NonNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class RedisWriterTokenTest {

  @Mock private @NonNull RLock lock;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private RedissonClient redisson;

  @Mock private Timer timer;

  @Nested
  class WhenConstructing {
    @Test
    void testClose() {
      when(redisson.getConfig().getLockWatchdogTimeout()).thenReturn(3L);
      when(lock.isLocked()).thenReturn(true);

      final RedisWriterToken uut = new RedisWriterToken(redisson, lock, timer);

      assertThat(uut.isValid()).isTrue();
      verify(timer).scheduleAtFixedRate(any(), anyLong(), eq(2L));
    }
  }

  @Nested
  class WhenClosing {
    @Test
    void testClose() throws Exception {
      final RedisWriterToken uut = new RedisWriterToken(redisson, lock, timer);
      uut.close();

      verify(lock).unlock();
      verify(timer).cancel();
    }
  }

  @Nested
  class WhenCheckingIfIsValid {
    @Test
    void testIsValid() {
      when(lock.isLocked()).thenReturn(true, false);

      final RedisWriterToken uut = new RedisWriterToken(redisson, lock, timer);

      assertThat(uut.isValid()).isTrue();

      final ArgumentCaptor<TimerTask> taskCaptor = ArgumentCaptor.forClass(TimerTask.class);
      verify(timer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong());

      final TimerTask task = taskCaptor.getValue();
      task.run();

      assertThat(uut.isValid()).isFalse();
    }
  }
}
