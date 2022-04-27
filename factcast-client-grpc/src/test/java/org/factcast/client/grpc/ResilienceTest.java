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
package org.factcast.client.grpc;

import static org.assertj.core.api.Assertions.*;

import com.google.common.base.Stopwatch;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.time.Duration;
import lombok.NonNull;
import lombok.SneakyThrows;
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
      config.setAttempts(3).setWindow(Duration.ofMinutes(1));
    }

    @Test
    void exhausts() {
      assertThat(underTest.attemptsExhausted()).isFalse();
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
      config.setAttempts(3).setWindow(Duration.ofMinutes(1));
    }

    @Test
    void deniesWhenExhausted() {
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
      config.setAttempts(2).setInterval(dur);
      underTest.sleepForInterval();
      assertThat(dur).isLessThan(sw.stop().elapsed());
    }
  }

  @Nested
  class WhenSleepingInterruptKeepsFlag {
    @SneakyThrows
    @Test
    void callsThreadSleep() {
      var t =
          new Thread(
              () -> {
                Duration dur = Duration.ofMillis(200);
                underTest.sleepForInterval();
              });
      t.start();
      t.interrupt();
      assertThat(t.isInterrupted()).isTrue();
    }
  }
}
