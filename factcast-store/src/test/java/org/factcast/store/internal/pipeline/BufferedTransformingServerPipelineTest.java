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

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.subscription.TransformationException;
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
import org.testcontainers.shaded.org.checkerframework.checker.nullness.qual.NonNull;

@ExtendWith(MockitoExtension.class)
public class BufferedTransformingServerPipelineTest {
  @Mock FactTransformerService service;
  @Mock FactTransformers transformers;
  @Mock ServerPipeline parent;
  @Mock PgMetrics metrics;
  private BufferedTransformingServerPipeline uut;

  @Nested
  class BufferingMode {
    @Mock private TransformationRequest transformationRequest;
    @Mock private TransformationRequest transformationRequest2;

    @Mock private Fact fact;

    @BeforeEach
    void setUp() {
      uut = new BufferedTransformingServerPipeline(parent, service, transformers, 5);
    }

    @Test
    void afterSwitchingToBufferingModeFlushesForNoopTransformations() {
      when(transformers.prepareTransformation(any()))
          .thenReturn(transformationRequest)
          .thenReturn(null);

      when(service.transform(anyList())).thenReturn(List.of(fact));

      uut.process(new Signal.FactSignal(fact));
      verifyNoInteractions(parent);
      uut.process(new Signal.FactSignal(fact));
      verifyNoInteractions(parent);
      uut.process(new Signal.FactSignal(fact));

      // maxBufferSize is 5, so we expect nothing yet
      verifyNoInteractions(parent);
      uut.process(new Signal.FlushSignal());

      verify(parent, times(3)).process(any(Signal.FactSignal.class));
      verify(parent).process(any(Signal.FlushSignal.class));
      verify(service).transform(List.of(transformationRequest));
    }

    @Test
    void afterSwitchingToBufferingModeFlushesAfterMaxSizeReached() {
      final var factToTransform = mock(Fact.class);
      final var noopFact = mock(Fact.class);
      final var factToTransform2 = mock(Fact.class);

      TransformationRequest transformationRequest =
          new TransformationRequest(factToTransform, Set.of(1));
      TransformationRequest transformationRequest2 =
          new TransformationRequest(factToTransform2, Set.of(1));
      when(transformers.prepareTransformation(any()))
          .thenReturn(transformationRequest, null, transformationRequest2, null, null);

      when(service.transform(List.of(transformationRequest, transformationRequest2)))
          .thenReturn(List.of(factToTransform, factToTransform2));

      uut.process(new Signal.FactSignal(factToTransform));
      verifyNoInteractions(parent);
      uut.process(new Signal.FactSignal(noopFact));
      verifyNoInteractions(parent);
      uut.process(new Signal.FactSignal(factToTransform2));
      uut.process(new Signal.FactSignal(noopFact));
      verifyNoInteractions(parent);
      uut.process(new Signal.FactSignal(noopFact));

      verify(parent, times(5)).process(any(Signal.FactSignal.class));
      verify(service).transform(List.of(transformationRequest, transformationRequest2));
    }

    @Test
    void escalatesTransformationException() {
      final var factToTransform = mock(Fact.class);
      final var noopFact = mock(Fact.class);
      final var factToTransform2 = mock(Fact.class);

      TransformationRequest transformationRequest =
          new TransformationRequest(factToTransform, Set.of(1));
      TransformationRequest transformationRequest2 =
          new TransformationRequest(factToTransform2, Set.of(1));
      when(transformers.prepareTransformation(any()))
          .thenReturn(transformationRequest, null, transformationRequest2, null, null);

      when(service.transform(List.of(transformationRequest, transformationRequest2)))
          .thenThrow(new TransformationException("bad luck"));

      uut.process(new Signal.FactSignal(factToTransform));
      verifyNoInteractions(parent);
      uut.process(new Signal.FactSignal(noopFact));
      verifyNoInteractions(parent);
      uut.process(new Signal.FactSignal(factToTransform2));
      uut.process(new Signal.FactSignal(noopFact));
      verifyNoInteractions(parent);
      // should trigger flush
      uut.process(new Signal.FactSignal(noopFact));

      verify(parent).process(any(Signal.ErrorSignal.class));
      verify(parent, never()).process(any(Signal.FactSignal.class));
    }
  }

  @Nested
  class DirectMode {

    @Mock private Fact fact;

    @BeforeEach
    void setUp() {
      uut = spy(new BufferedTransformingServerPipeline(parent, service, transformers, 50));
    }

    @Test
    void simplePassThrough() {
      when(transformers.prepareTransformation(any())).thenReturn(null);
      Signal signal = new Signal.FactSignal(fact);
      uut.process(signal);
      verify(parent).process(signal);
      uut.process(signal);
      verify(parent, times(2)).process(signal);
      uut.process(signal);
      verify(parent, times(3)).process(signal);

      verifyNoInteractions(service);
    }

    @Test
    void simplePassNonFact() {
      Signal signal = new Signal.FlushSignal();

      uut.process(signal);
      verify(parent).process(signal);

      uut.process(new Signal.CatchupSignal());
      verify(parent, times(2)).process(any());

      uut.process(new Signal.CompleteSignal());
      verify(parent, times(3)).process(any());

      verifyNoInteractions(service, transformers);
    }
  }

  @Nested
  class Flushing {
    @BeforeEach
    void setUp() {
      uut = spy(new BufferedTransformingServerPipeline(parent, service, transformers, 50));
    }

    @Test
    void flushesOnCatchup() {
      uut.flushIfNecessary(new Signal.CatchupSignal());
      verify(uut).doFlush();
    }

    @Test
    void flushesOnComplete() {
      uut.flushIfNecessary(new Signal.CompleteSignal());
      verify(uut).doFlush();
    }

    @Test
    void flushesOnError() {
      uut.flushIfNecessary(new Signal.ErrorSignal(new IOException("buh")));
      verify(uut).doFlush();
    }

    //
    @Test
    void doesNotFLushOnFact() {
      @NonNull Fact fact = new TestFact();
      uut.flushIfNecessary(new Signal.FactSignal(fact));
      verify(uut).flushIfNecessary(any());
      verifyNoMoreInteractions(uut);
    }

    @Test
    void flushesOnFlush() {
      uut.flushIfNecessary(new Signal.FlushSignal());
      verify(uut).doFlush();
    }
  }
}
