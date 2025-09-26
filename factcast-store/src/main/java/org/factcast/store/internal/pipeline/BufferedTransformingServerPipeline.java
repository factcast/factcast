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
package org.factcast.store.internal.pipeline;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import java.util.*;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.TransformationException;
import org.factcast.store.internal.PgFact;
import org.factcast.store.internal.transformation.FactTransformerService;
import org.factcast.store.internal.transformation.FactTransformers;
import org.factcast.store.internal.transformation.TransformationRequest;

/**
 * this class is NOT Threadsafe!
 *
 * <p>Also, we moved away from flushing asynchronously, as it wont have an impact on overall
 * performance but makes exception handling much harder. Please note that transformations of a list
 * of requests is handled in parallel already, to minimize the blocking time of flushing afap.
 */
@Slf4j
public class BufferedTransformingServerPipeline extends AbstractServerPipeline {

  private final FactTransformerService service;
  private final FactTransformers transformers;
  private final int maxBufferSize;
  //
  private final List<Supplier<Signal>> buffer;
  private Mode mode = Mode.DIRECT;

  enum Mode {
    DIRECT,
    BUFFERING
  }

  @RequiredArgsConstructor
  static class TransformedFactSupplier implements Supplier<Signal> {
    @Getter final TransformationRequest transformationRequest;
    @Setter PgFact resolved;

    @Override
    public Signal.FactSignal get() {
      return Signal.of(resolved);
    }
  }

  public BufferedTransformingServerPipeline(
      @NonNull ServerPipeline parent,
      @NonNull FactTransformerService service,
      @NonNull FactTransformers transformers,
      int maxBufferSize) {
    super(parent);
    Preconditions.checkArgument(maxBufferSize > 2, "maxBufferSize must be >2");
    this.service = service;
    this.transformers = transformers;
    this.maxBufferSize = maxBufferSize;
    buffer = new ArrayList<>(maxBufferSize);
  }

  @Override
  public void process(@NonNull Signal s) {

    if (!(s instanceof Signal.FactSignal signal)) {
      // anything but facts go to parent/buffer directly
      passOrBuffer(s);
    } else {

      PgFact fact = signal.fact();

      TransformationRequest transformationRequest = transformers.prepareTransformation(fact);

      if (transformationRequest == null) {
        passOrBuffer(s);
      } else {
        // needs transformation
        log.trace("passing fact signal WITH transformation: {}", fact);

        // switch to buffering no matter what it was before
        mode = Mode.BUFFERING;
        buffer(transformationRequest);
      }
    }
  }

  @VisibleForTesting
  void passOrBuffer(@NonNull Signal s) {
    if (mode == Mode.DIRECT) {
      parent.process(s);
    } else {
      // buffered mode
      buffer(s);
    }
  }

  void buffer(Signal s) {
    buffer.add(() -> s);
    flushIfNecessary(s);
  }

  void buffer(TransformationRequest transformationRequest) {
    buffer.add(new TransformedFactSupplier(transformationRequest));
    flushIfNecessary();
  }

  void flushIfNecessary(@Nullable Signal s) {
    if (buffer.size() >= maxBufferSize || (s != null && s.indicatesFlush())) {
      doFlush();
    }
  }

  void flushIfNecessary() {
    flushIfNecessary(null);
  }

  @VisibleForTesting
  void doFlush() {
    if (!buffer.isEmpty()) {
      try {
        int size = buffer.size();
        if (size > 2) {
          // we only want that trace log if this is more that "Fact+Flush"
          log.trace("flushing buffer of size {}", size);
        }

        FluentIterable<TransformedFactSupplier> pendingTransformations =
            FluentIterable.from(buffer).filter(TransformedFactSupplier.class);

        if (pendingTransformations.isEmpty()) {
          throw new IllegalStateException("No pending transformations in buffer!");
        }

        try {

          List<TransformationRequest> requests =
              pendingTransformations.stream()
                  .map(TransformedFactSupplier::transformationRequest)
                  .toList();

          List<PgFact> transformedFacts = service.transform(requests);

          if (pendingTransformations.size() != transformedFacts.size()) {
            throw new IllegalStateException(
                "transformation resulted in unexpected number of facts");
          }

          // pass results back to TransformedFactSuppliers
          Iterator<PgFact> transformedIterator = transformedFacts.iterator();
          pendingTransformations.forEach(t -> t.resolved(transformedIterator.next()));

          buffer.stream().map(Supplier::get).forEach(parent::process);
        } catch (TransformationException e) {
          // swallows the signals at the beginning of the buffer.
          parent.process(Signal.of(e));
        }
      } finally {
        // reset buffer
        buffer.clear();
        mode = Mode.DIRECT;
      }
    }
  }
}
