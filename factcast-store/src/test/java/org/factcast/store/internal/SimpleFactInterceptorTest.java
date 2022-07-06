package org.factcast.store.internal;

import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.store.internal.filter.FactFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.NonNull;

import static org.mockito.ArgumentMatchers.same;

@ExtendWith(MockitoExtension.class)
class SimpleFactInterceptorTest {
  @Mock private FactTransformerService service;
  @Mock private FactTransformers transformers;
  @Mock private FactFilter filter;

  @Mock(strictness = Mock.Strictness.LENIENT, answer = Answers.RETURNS_DEEP_STUBS)
  private PgMetrics metrics;

  @Mock private SubscriptionImpl targetSubscription;
  @InjectMocks private SimpleFactInterceptor underTest;

  @Nested
  class WhenAccepting {
    @Mock private @NonNull Fact f;
    @Mock private TransformationRequest req;
    private Fact t;

    @BeforeEach
    void setup() {}

    @Test
    void filters() {
      Mockito.when(filter.test(f)).thenReturn(false);

      underTest.accept(f);

      Mockito.verifyNoInteractions(targetSubscription, transformers, service);
    }

    @Test
    void transforms() {
      Mockito.when(filter.test(f)).thenReturn(true);
      Mockito.when(transformers.prepareTransformation(f)).thenReturn(req);
      Mockito.when(service.transform(req)).thenReturn(t);

      underTest.accept(f);

      Mockito.verify(targetSubscription).notifyElement(same(t));

      Mockito.verifyNoMoreInteractions(targetSubscription, transformers, service);
    }

    @Test
    void throwingDuringTtransformation() {
      Mockito.when(filter.test(f)).thenReturn(true);
      Mockito.when(transformers.prepareTransformation(f)).thenReturn(req);
      Mockito.when(service.transform(req)).thenThrow(TransformationException.class);

      Assertions.assertThatThrownBy(
              () -> {
                underTest.accept(f);
              })
          .isInstanceOf(TransformationException.class);
    }

    @Test
    void passesAlong() {
      Mockito.when(filter.test(f)).thenReturn(true);
      Mockito.when(transformers.prepareTransformation(f)).thenReturn(null);

      underTest.accept(f);

      Mockito.verify(targetSubscription).notifyElement(same(f));

      Mockito.verifyNoMoreInteractions(targetSubscription, transformers, service);
    }
  }
}
