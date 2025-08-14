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
import org.factcast.core.subscription.TransformationException;
import org.factcast.store.internal.*;
import org.factcast.store.internal.transformation.FactTransformerService;
import org.factcast.store.internal.transformation.FactTransformers;
import org.factcast.store.internal.transformation.TransformationRequest;
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

    @Mock private PgFact fact;

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

      uut.process(Signal.of(fact));
      verifyNoInteractions(parent);
      uut.process(Signal.of(fact));
      verifyNoInteractions(parent);
      uut.process(Signal.of(fact));

      // maxBufferSize is 5, so we expect nothing yet
      verifyNoInteractions(parent);
      uut.process(Signal.flush());

      verify(parent, times(3)).process(any(Signal.FactSignal.class));
      verify(parent).process(any(Signal.FlushSignal.class));
      verify(service).transform(List.of(transformationRequest));
    }

    @Test
    void afterSwitchingToBufferingModeFlushesAfterMaxSizeReached() {
      final var factToTransform = mock(PgFact.class);
      final var noopFact = mock(PgFact.class);
      final var factToTransform2 = mock(PgFact.class);

      TransformationRequest t1 = new TransformationRequest(factToTransform, Set.of(1));
      TransformationRequest t2 = new TransformationRequest(factToTransform2, Set.of(1));
      when(transformers.prepareTransformation(any())).thenReturn(t1, null, t2, null, null);

      when(service.transform(List.of(t1, t2)))
          .thenReturn(List.of(factToTransform, factToTransform2));

      uut.process(Signal.of(factToTransform));
      verifyNoInteractions(parent);
      uut.process(Signal.of(noopFact));
      verifyNoInteractions(parent);
      uut.process(Signal.of(factToTransform2));
      uut.process(Signal.of(noopFact));
      verifyNoInteractions(parent);
      uut.process(Signal.of(noopFact));

      verify(parent, times(5)).process(any(Signal.FactSignal.class));
      verify(service).transform(List.of(t1, t2));
    }

    @Test
    void escalatesTransformationException() {
      final var factToTransform = mock(PgFact.class);
      final var noopFact = mock(PgFact.class);
      final var factToTransform2 = mock(PgFact.class);

      TransformationRequest t1 = new TransformationRequest(factToTransform, Set.of(1));
      TransformationRequest t2 = new TransformationRequest(factToTransform2, Set.of(1));
      when(transformers.prepareTransformation(any())).thenReturn(t1, null, t2, null, null);

      when(service.transform(List.of(t1, t2))).thenThrow(new TransformationException("bad luck"));

      uut.process(Signal.of(factToTransform));
      verifyNoInteractions(parent);
      uut.process(Signal.of(noopFact));
      verifyNoInteractions(parent);
      uut.process(Signal.of(factToTransform2));
      uut.process(Signal.of(noopFact));
      verifyNoInteractions(parent);
      // should trigger flush
      uut.process(Signal.of(noopFact));

      verify(parent).process(any(Signal.ErrorSignal.class));
      verify(parent, never()).process(any(Signal.FactSignal.class));
    }
  }

  @Nested
  class DirectMode {

    @Mock private PgFact fact;

    @BeforeEach
    void setUp() {
      uut = spy(new BufferedTransformingServerPipeline(parent, service, transformers, 50));
    }

    @Test
    void simplePassThrough() {
      when(transformers.prepareTransformation(any())).thenReturn(null);
      Signal signal = Signal.of(fact);
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
      Signal signal = Signal.flush();

      uut.process(signal);
      verify(parent).process(signal);

      uut.process(Signal.catchup());
      verify(parent, times(2)).process(any());

      uut.process(Signal.complete());
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
      uut.flushIfNecessary(Signal.catchup());
      verify(uut).doFlush();
    }

    @Test
    void flushesOnComplete() {
      uut.flushIfNecessary(Signal.complete());
      verify(uut).doFlush();
    }

    @Test
    void flushesOnError() {
      uut.flushIfNecessary(Signal.of(new IOException("buh")));
      verify(uut).doFlush();
    }

    //
    @Test
    void doesNotFLushOnFact() {
      @NonNull PgFact fact = mock(PgFact.class);
      uut.flushIfNecessary(Signal.of(fact));
      verify(uut).flushIfNecessary(any());
      verifyNoMoreInteractions(uut);
    }

    @Test
    void flushesOnFlush() {
      uut.flushIfNecessary(Signal.flush());
      verify(uut).doFlush();
    }
  }
}
