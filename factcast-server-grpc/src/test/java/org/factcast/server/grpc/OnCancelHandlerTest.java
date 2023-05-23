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
package org.factcast.server.grpc;

import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnCancelHandlerTest {
  private static final String CLIENT_ID_PREFIX = "CLIENT_ID_PREFIX";
  @Mock private @NonNull SubscriptionRequestTO req;
  @Mock private @NonNull GrpcObserverAdapter observer;

  @Nested
  class WhenRuning {
    @Mock private Subscription subscription;

    private OnCancelHandler underTest;

    @BeforeEach
    void setup() {
      AtomicReference<Subscription> subRef = new AtomicReference<>(subscription);
      underTest = spy(new OnCancelHandler("id", req, subRef, observer));
    }

    @SneakyThrows
    @Test
    void closesSubscription() {
      underTest.run();

      verify(subscription).close();
    }

    @SneakyThrows
    @Test
    void shutsDownObserver() {
      underTest.run();

      verify(subscription).close();
      verify(observer).shutdown();
    }

    @SneakyThrows
    @Test
    void shutsDownObserverEvenAfterSubscriptionThrows() {
      doThrow(new RuntimeException("ignore")).when(subscription).close();
      underTest.run();

      verify(subscription).close();
      verify(observer).shutdown();
    }

    @SneakyThrows
    @Test
    void survivesEveryoneThrowingExceptions() {
      doThrow(new RuntimeException("ignore")).when(subscription).close();
      doThrow(new RuntimeException("ignore")).when(observer).shutdown();
      underTest.run();

      verify(subscription).close();
      verify(observer).shutdown();
    }
  }
}
