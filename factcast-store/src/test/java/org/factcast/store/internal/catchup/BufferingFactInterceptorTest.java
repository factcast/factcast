/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.store.internal.catchup;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BufferingFactInterceptorTest {
  //  @Mock FactTransformerService service;
  //  @Mock FactTransformers transformers;
  //  @Mock FactFilter filter;
  //  @Mock ServerSideFactObserver obs;
  //  @Mock SubscriptionImpl targetSubscription;
  //
  //  private final ExecutorService es = Executors.newSingleThreadExecutor();
  //  @Mock private @NonNull PgMetrics metrics;
  //  private TransformingFactConsumer uut;
  //
  //  @Nested
  //  class BufferingMode {
  //    @Mock private TransformationRequest transformationRequest;
  //
  //    @Mock private Fact fact;
  //
  //    @BeforeEach
  //    void setUp() {
  //      targetSubscription = new SubscriptionImpl(obs);
  //      uut =
  //          new TransformingFactConsumer(
  //              service, transformers, filter, targetSubscription, 3, metrics, es);
  //    }
  //
  //    @Test
  //    void afterSwitchingToBufferingModeFlushesForNoopTransformations() {
  //      when(metrics.counter(any())).thenReturn(mock(Counter.class));
  //      when(transformers.prepareTransformation(any()))
  //          .thenReturn(transformationRequest)
  //          .thenReturn(null);
  //      when(filter.test(any())).thenReturn(true);
  //      when(transformationRequest.toTransform()).thenReturn(fact);
  //      when(fact.id()).thenReturn(UUID.randomUUID());
  //
  //      when(service.transform(anyList())).thenReturn(List.of(fact));
  //
  //      uut.accept(fact);
  //      verifyNoInteractions(obs);
  //      uut.accept(fact);
  //      verifyNoInteractions(obs);
  //      uut.accept(fact);
  //
  //      // maxBufferSize is 3, so we expect a flush = 3 notifyElement call
  //      verify(obs, times(3)).onNext(fact);
  //      verify(service).transform(List.of(transformationRequest));
  //    }
  //
  //    @Test
  //    void afterSwitchingToBufferingModeFlushesAfterMaxSizeReached() {
  //      final var factToTransform = mock(Fact.class);
  //      final var noopFact = mock(Fact.class);
  //      final var factToTransform2 = mock(Fact.class);
  //
  //      when(metrics.counter(any())).thenReturn(mock(Counter.class));
  //      when(filter.test(any())).thenReturn(true);
  //
  //      when(transformers.prepareTransformation(any()))
  //          .thenReturn(transformationRequest, null, transformationRequest);
  //      when(transformationRequest.toTransform()).thenReturn(factToTransform, factToTransform2);
  //      when(factToTransform.id()).thenReturn(UUID.randomUUID());
  //      when(factToTransform2.id()).thenReturn(UUID.randomUUID());
  //
  //      when(service.transform(anyList())).thenReturn(List.of(factToTransform, factToTransform2));
  //
  //      uut.accept(factToTransform);
  //      verifyNoInteractions(obs);
  //      uut.accept(noopFact);
  //      verifyNoInteractions(obs);
  //      uut.accept(factToTransform2);
  //
  //      // maxBufferSize is 3, so we expect a flush = 3 notifyElement call
  //      verify(obs).onNext(factToTransform);
  //      verify(obs).onNext(noopFact);
  //      verify(obs).onNext(factToTransform2);
  //      verify(service).transform(List.of(transformationRequest, transformationRequest));
  //    }
  //  }
  //
  //  @Nested
  //  class DirectMode {
  //
  //    @Mock private Fact fact;
  //
  //    @BeforeEach
  //    void setUp() {
  //      targetSubscription = new SubscriptionImpl(obs);
  //      uut =
  //          new TransformingFactConsumer(
  //              service, transformers, filter, targetSubscription, 50, metrics, es);
  //    }
  //
  //    @Test
  //    void simplePassThrough() {
  //      when(metrics.counter(any())).thenReturn(mock(Counter.class));
  //      when(transformers.prepareTransformation(any())).thenReturn(null);
  //      when(filter.test(any())).thenReturn(true);
  //
  //      uut.accept(fact);
  //      verify(obs).onNext(fact);
  //      uut.accept(fact);
  //      verify(obs, times(2)).onNext(fact);
  //      uut.accept(fact);
  //      verify(obs, times(3)).onNext(fact);
  //
  //      verifyNoInteractions(service);
  //    }
  //  }

  // TODO
}
