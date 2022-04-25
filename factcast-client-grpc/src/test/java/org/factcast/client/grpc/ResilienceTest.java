package org.factcast.client.grpc;

import static org.assertj.core.api.Assertions.*;

import com.google.common.base.Stopwatch;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.time.Duration;
import lombok.NonNull;
import org.factcast.client.grpc.FactCastGrpcClientProperties.ResilienceConfiguration;
import org.factcast.core.store.RetryableException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResilienceTest {

  @Spy
  private @NonNull FactCastGrpcClientProperties.ResilienceConfiguration config =
      new ResilienceConfiguration();

  @InjectMocks private Resilience underTest;

  @Nested
  class WhenAttemptsExhausted {
    @BeforeEach
    void setup() {
      config.setRetries(3).setWindow(Duration.ofMinutes(1));
    }

    @Test
    void exhausts() {
      assertThat(underTest.attemptsExhausted()).isFalse();
      underTest.registerAttempt();
      underTest.registerAttempt();
      underTest.registerAttempt();
      assertThat(underTest.attemptsExhausted()).isFalse();
      underTest.registerAttempt();
      assertThat(underTest.attemptsExhausted()).isTrue();
    }
  }

  @Nested
  class WhenRegisteringAttempt {
    @BeforeEach
    void setup() {}

    @Test
    void registers() {
      underTest.registerAttempt();
      assertThat(underTest.numberOfAttemptsInWindow()).isEqualTo(1);
      underTest.registerAttempt();
      assertThat(underTest.numberOfAttemptsInWindow()).isEqualTo(2);
    }
  }

  @Nested
  class WhenShouldRetry {
    @Mock private Throwable exception;

    @BeforeEach
    void setup() {
      config.setRetries(3).setWindow(Duration.ofMinutes(1));
    }

    @Test
    void deniesWhenExhausted() {
      underTest.registerAttempt();
      underTest.registerAttempt();
      underTest.registerAttempt();
      assertThat(underTest.shouldRetry(new RetryableException(new IOException()))).isTrue();
      underTest.registerAttempt();
      assertThat(underTest.shouldRetry(new RetryableException(new IOException()))).isFalse();
    }

    @Test
    void deniesWhenExceptionNotRetryable() {
      assertThat(underTest.shouldRetry(new StatusRuntimeException(Status.UNKNOWN))).isTrue();
      assertThat(underTest.shouldRetry(new StatusRuntimeException(Status.UNAVAILABLE))).isTrue();
      assertThat(underTest.shouldRetry(new StatusRuntimeException(Status.ABORTED))).isTrue();
      assertThat(underTest.shouldRetry(new StatusRuntimeException(Status.DEADLINE_EXCEEDED)))
          .isTrue();

      assertThat(underTest.shouldRetry(new StatusRuntimeException(Status.DATA_LOSS))).isFalse();
      assertThat(underTest.shouldRetry(new IOException())).isFalse();
    }
  }

  @Nested
  class WhenSleepingForInterval {
    @Test
    void callsThreadSleep() {
      Stopwatch sw = Stopwatch.createStarted();
      Duration dur = Duration.ofMillis(50);
      config.setRetries(2).setInterval(dur);
      underTest.sleepForInterval();
      assertThat(dur).isLessThan(sw.stop().elapsed());
    }
  }
}
