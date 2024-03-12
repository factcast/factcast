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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Disabled // TODO
public class BufferingFactInterceptorTest {
  //  @Mock private FactTransformerService service;
  //  @Mock private FactTransformers transformers;
  //  @Mock private FactFilter filter;
  //  @Mock private SubscriptionImpl targetSubscription;
  //
  //  private final ExecutorService es = Executors.newSingleThreadExecutor();
  //  @Mock private @NonNull PgMetrics metrics;
  //  private BufferedTransformingFactPipeline uut;
  //
  //  @Nested
  //  class BufferingMode {
  //    @Mock private TransformationRequest transformationRequest;
  //
  //    @Mock private Fact fact;
  //
  //    @BeforeEach
  //    void setUp() {
  //      uut =
  //          new BufferedTransformingFactPipeline(
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
  //      verifyNoInteractions(targetSubscription);
  //      uut.accept(fact);
  //      verifyNoInteractions(targetSubscription);
  //      uut.accept(fact);
  //
  //      // maxBufferSize is 3, so we expect a flush = 3 notifyElement call
  //      verify(targetSubscription, times(3)).notifyElement(fact);
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
  //      verifyNoInteractions(targetSubscription);
  //      uut.accept(noopFact);
  //      verifyNoInteractions(targetSubscription);
  //      uut.accept(factToTransform2);
  //
  //      // maxBufferSize is 3, so we expect a flush = 3 notifyElement call
  //      verify(targetSubscription).notifyElement(factToTransform);
  //      verify(targetSubscription).notifyElement(noopFact);
  //      verify(targetSubscription).notifyElement(factToTransform2);
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
  //      uut =
  //          new BufferedTransformingFactPipeline(
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
  //      verify(targetSubscription).notifyElement(fact);
  //      uut.accept(fact);
  //      verify(targetSubscription, times(2)).notifyElement(fact);
  //      uut.accept(fact);
  //      verify(targetSubscription, times(3)).notifyElement(fact);
  //
  //      verifyNoInteractions(service);
  //    }
  //  }
}
