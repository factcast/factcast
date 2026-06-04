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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.Fact;
import org.factcast.factus.Factus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetryableTransactionImplTest {

  @Mock private Supplier<Fact> factSupplier;
  @Mock private List<Supplier<Fact>> factSuppliers;
  @Mock private Factus factus;
  @Mock private Runnable onSuccess;
  @InjectMocks private RetryableTransactionImpl underTest;

  @Nested
  class WhenOningSuccess {
    @Mock private @NonNull Runnable willBeRunOnSuccessOnly;

    @Test
    void initiallyEmpty() {
      assertThat(underTest.onSuccess()).isEmpty();
    }

    @Test
    void executes() {
      CountDownLatch cl = new CountDownLatch(1);
      underTest.onSuccess(cl::countDown);

      assertThat(underTest.onSuccess()).isNotEmpty();
      underTest.onSuccess().get().run();
      assertThat(cl.getCount()).isZero();
    }

    @Test
    void chainsExecutions() {
      CountDownLatch cl = new CountDownLatch(3);
      underTest.onSuccess(cl::countDown);
      underTest.onSuccess(cl::countDown);
      underTest.onSuccess(cl::countDown);

      assertThat(underTest.onSuccess()).isNotEmpty();
      underTest.onSuccess().get().run();

      assertThat(cl.getCount()).isZero();
    }
  }

  @Nested
  class WhenOningSuccessAsync {
    @Mock private @NonNull Runnable willBeRunOnSuccessOnly;

    @SuppressWarnings({"OptionalGetWithoutIsPresent", "ResultOfMethodCallIgnored"})
    @SneakyThrows
    @Test
    void wrapsInFuture() {
      CountDownLatch cl = new CountDownLatch(1);
      underTest.onSuccessAsync(cl::countDown);

      underTest.onSuccess().get().run();
      cl.await(300, TimeUnit.MILLISECONDS);
    }
  }
}
