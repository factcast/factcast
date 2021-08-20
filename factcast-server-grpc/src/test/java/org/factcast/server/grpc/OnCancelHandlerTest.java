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
