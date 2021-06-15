package org.factcast.factus.redis;

import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
