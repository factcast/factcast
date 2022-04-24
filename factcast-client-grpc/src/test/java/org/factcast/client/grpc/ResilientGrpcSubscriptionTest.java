/*
 * Copyright © 2017-2020 factcast.org
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.factcast.client.grpc.FactCastGrpcClientProperties.ResilienceConfiguration;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionClosedException;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResilientGrpcSubscriptionTest {
  @Mock(lenient = true)
  private GrpcFactStore store;

  @Mock private SubscriptionRequestTO req;

  @Mock private FactObserver obs;

  @Mock private Subscription subscription;

  private final ArgumentCaptor<FactObserver> observerAC =
      ArgumentCaptor.forClass(FactObserver.class);

  ResilientGrpcSubscription uut;

  @BeforeEach
  public void setup() {
    // order is important here, you have been warned
    when(store.internalSubscribe(any(), observerAC.capture())).thenReturn(subscription);
    uut = new ResilientGrpcSubscription(store, req, obs, new ResilienceConfiguration());
    when(store.subscribe(any(), observerAC.capture())).thenReturn(uut);
  }

  @Test
  void testAwaitCompleteDelegatesToSubscription() {
    // needs to return immediately
    uut.awaitComplete();
    verify(subscription).awaitComplete();
  }

  @Test
  void testAwaitCatchupDelegatesToSubscription() {
    // needs to return immediately
    uut.awaitCatchup();
    verify(subscription).awaitCatchup();
  }

  @Test
  void testAwaitCompleteLong() throws Exception {
    when(subscription.awaitComplete(anyLong()))
        .thenThrow(TimeoutException.class)
        .then(x -> subscription);

    assertThrows(TimeoutException.class, () -> uut.awaitComplete(51));

    assertTimeout(
        Duration.ofMillis(1000),
        () -> {
          assertThat(uut.awaitComplete(52)).isSameAs(uut);
        });
    // await call was passed
    verify(subscription).awaitComplete(52);
  }

  @Test
  void testAwaitCatchupLong() throws Exception {
    when(subscription.awaitCatchup(anyLong()))
        .thenThrow(TimeoutException.class)
        .then(x -> subscription);

    assertThrows(TimeoutException.class, () -> uut.awaitCatchup(51));

    assertTimeout(
        Duration.ofMillis(1000),
        () -> {
          assertThat(uut.awaitCatchup(52)).isSameAs(uut);
        });
    // await call was passed
    verify(subscription).awaitCatchup(52);
  }

  @Test
  void testAssertSubscriptionStateNotClosed() throws Exception {
    uut.close();
    assertThrows(SubscriptionClosedException.class, () -> uut.awaitCatchup());
    assertThrows(SubscriptionClosedException.class, () -> uut.awaitCatchup(1L));
    assertThrows(SubscriptionClosedException.class, () -> uut.awaitComplete());
    assertThrows(SubscriptionClosedException.class, () -> uut.awaitComplete(1L));
  }

  @Test
  void isServerException() throws Exception {

    assertThat(ClientExceptionHelper.isRetryable(new RuntimeException())).isFalse();
    assertThat(ClientExceptionHelper.isRetryable(new IllegalArgumentException())).isFalse();
    assertThat(ClientExceptionHelper.isRetryable(new IOException())).isFalse();
    assertThat(
            ClientExceptionHelper.isRetryable(new StatusRuntimeException(Status.UNAUTHENTICATED)))
        .isFalse();
    assertThat(
            ClientExceptionHelper.isRetryable(new StatusRuntimeException(Status.PERMISSION_DENIED)))
        .isFalse();
    assertThat(
            ClientExceptionHelper.isRetryable(
                new StatusRuntimeException(Status.RESOURCE_EXHAUSTED)))
        .isFalse();
    assertThat(
            ClientExceptionHelper.isRetryable(new StatusRuntimeException(Status.INVALID_ARGUMENT)))
        .isFalse();

    //
    assertThat(ClientExceptionHelper.isRetryable(new StatusRuntimeException(Status.UNKNOWN)))
        .isTrue();
    assertThat(ClientExceptionHelper.isRetryable(new StatusRuntimeException(Status.UNAVAILABLE)))
        .isTrue();
    assertThat(ClientExceptionHelper.isRetryable(new StatusRuntimeException(Status.ABORTED)))
        .isTrue();

    // assertThat(uut.isNotRetryable(new TransformationExceptione());
    // assertThat(uut.isNotRetryable(new MissingTransformationInformationException());
    // important because it needs to reconnect, which only happens if it is NOT categorized as
    // serverException
    // assertThat(uut.isNotRetryable(new StaleSubscriptionDetectedException())).isFalse();
  }
}
