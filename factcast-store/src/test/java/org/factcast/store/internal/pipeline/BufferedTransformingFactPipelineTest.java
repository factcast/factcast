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
package org.factcast.store.internal.pipeline;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.factcast.core.Fact;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.store.internal.PgMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BufferedTransformingFactPipelineTest {
  final ExecutorService es = Executors.newSingleThreadExecutor();
  @Mock FactTransformerService service;
  @Mock FactTransformers transformers;
  @Mock FactPipeline parent;
  @Mock PgMetrics metrics;
  private BufferedTransformingFactPipeline uut;

  @Nested
  class BufferingMode {
    @Mock private TransformationRequest transformationRequest;

    @Mock private Fact fact;

    @BeforeEach
    void setUp() {
      uut = new BufferedTransformingFactPipeline(parent, service, transformers, 3, es);
    }

    @Test
    void afterSwitchingToBufferingModeFlushesForNoopTransformations() {
      when(transformers.prepareTransformation(any()))
          .thenReturn(transformationRequest)
          .thenReturn(null);
      when(transformationRequest.toTransform()).thenReturn(fact);
      when(fact.id()).thenReturn(UUID.randomUUID());

      when(service.transform(anyList())).thenReturn(List.of(fact));

      uut.fact(fact);
      verifyNoInteractions(parent);
      uut.fact(fact);
      verifyNoInteractions(parent);
      uut.fact(fact);

      // maxBufferSize is 3, so we expect a flush = 3 notifyElement call
      verify(parent, times(3)).fact(fact);
      verify(service).transform(List.of(transformationRequest));
    }

    @Test
    void afterSwitchingToBufferingModeFlushesAfterMaxSizeReached() {
      final var factToTransform = mock(Fact.class);
      final var noopFact = mock(Fact.class);
      final var factToTransform2 = mock(Fact.class);

      when(transformers.prepareTransformation(any()))
          .thenReturn(transformationRequest, null, transformationRequest);
      when(transformationRequest.toTransform()).thenReturn(factToTransform, factToTransform2);
      when(factToTransform.id()).thenReturn(UUID.randomUUID());
      when(factToTransform2.id()).thenReturn(UUID.randomUUID());

      when(service.transform(anyList())).thenReturn(List.of(factToTransform, factToTransform2));

      uut.fact(factToTransform);
      verifyNoInteractions(parent);
      uut.fact(noopFact);
      verifyNoInteractions(parent);
      uut.fact(factToTransform2);

      // maxBufferSize is 3, so we expect a flush = 3 notifyElement call
      verify(parent).fact(factToTransform);
      verify(parent).fact(noopFact);
      verify(parent).fact(factToTransform2);
      verify(service).transform(List.of(transformationRequest, transformationRequest));
    }
  }

  @Nested
  class DirectMode {

    @Mock private Fact fact;

    @BeforeEach
    void setUp() {
      uut = new BufferedTransformingFactPipeline(parent, service, transformers, 50, es);
    }

    @Test
    void simplePassThrough() {
      when(transformers.prepareTransformation(any())).thenReturn(null);

      uut.fact(fact);
      verify(parent).fact(fact);
      uut.fact(fact);
      verify(parent, times(2)).fact(fact);
      uut.fact(fact);
      verify(parent, times(3)).fact(fact);

      verifyNoInteractions(service);
    }
  }
}
