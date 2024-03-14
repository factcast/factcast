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
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.store.internal.Pair;

/** this class is NOT Threadsafe! */
@Slf4j
public class BufferedTransformingServerPipeline extends AbstractServerPipeline {
  public BufferedTransformingServerPipeline(
      ServerPipeline parent,
      FactTransformerService service,
      FactTransformers transformers,
      int maxBufferSize,
      ExecutorService es) {
    super(parent);
    this.service = service;
    this.transformers = transformers;
    this.maxBufferSize = maxBufferSize;
    buffer = new ArrayList<>(maxBufferSize);
    index = new HashMap<>(maxBufferSize);
    this.es = es;
  }

  enum Mode {
    DIRECT,
    BUFFERING
  }

  private final FactTransformerService service;
  private final FactTransformers transformers;
  private final int maxBufferSize;

  // maintains the order of *all* signals
  @SuppressWarnings("rawtypes")
  private final List<Pair<TransformationRequest, CompletableFuture<Signal>>> buffer;

  // enables fast lookup of prepared CFutures
  // note that only FactSignals are indexed
  private final Map<UUID, CompletableFuture<Signal.FactSignal>> index;

  private Mode mode = Mode.DIRECT;

  private final ExecutorService es;

  private void acceptInBufferingMode(
      @NonNull Signal.FactSignal s, TransformationRequest transformationRequest) {
    if (transformationRequest == null) {
      // does not need transformation, add as completed
      addTransformationToBuffer(completedTransformation(s.fact()));
    } else {
      addTransformationToBuffer(futureSignal(transformationRequest));
    }
  }

  @VisibleForTesting
  void flushIfNecessary(@Nullable Signal s) {
    if (buffer.size() >= maxBufferSize
        || s instanceof Signal.FlushSignal
        || s instanceof Signal.CatchupSignal
        || s instanceof Signal.CompleteSignal
        || s instanceof Signal.ErrorSignal) {
      doFlush();
    }
  }

  private void addTransformationToBuffer(
      Pair<TransformationRequest, CompletableFuture<Signal.FactSignal>> scheduledTransformation) {
    // weird indirection to bend the typing rules
    CompletableFuture<Signal> rawFuture = new CompletableFuture<>();
    scheduledTransformation.right().thenAccept(rawFuture::complete);
    buffer.add(Pair.of(scheduledTransformation.left(), rawFuture));

    TransformationRequest req = scheduledTransformation.left();
    if (req != null) {
      // index to help with access when completing
      index.put(req.toTransform().id(), scheduledTransformation.right());
    }
    flushIfNecessary();
  }

  private void flushIfNecessary() {
    flushIfNecessary(null);
  }

  private void acceptInDirectMode(
      @NonNull Signal.FactSignal f, TransformationRequest transformationRequest) {
    if (transformationRequest == null) {
      // does not need transformation, just pass it to parent
      parent.process(f);
    } else {
      // needs transformation, so switch to buffering mode
      mode = Mode.BUFFERING;
      addTransformationToBuffer(futureSignal(transformationRequest));
    }
  }

  @NonNull
  private Pair<TransformationRequest, CompletableFuture<Signal.FactSignal>> futureSignal(
      @NonNull TransformationRequest transformationRequest) {
    return Pair.of(transformationRequest, new CompletableFuture<>());
  }

  @NonNull
  private Pair<TransformationRequest, CompletableFuture<Signal.FactSignal>> completedTransformation(
      @NonNull Fact f) {
    return Pair.of(null, CompletableFuture.completedFuture(new Signal.FactSignal(f)));
  }

  @Override
  public void process(Signal s) {

    // TODO turn into type switch when using JDK21+

    if (!(s instanceof Signal.FactSignal signal)) {
      if (mode == Mode.DIRECT) {
        parent.process(s);
        return;
      } else {
        // buffered mode
        buffer.add(Pair.of(null, CompletableFuture.completedFuture(s)));
      }
      flushIfNecessary(s);

    } else {

      // buffer the signal as is
      TransformationRequest transformationRequest =
          transformers.prepareTransformation(signal.fact());

      if (mode == Mode.DIRECT) {
        acceptInDirectMode(signal, transformationRequest);
      } else {
        if (mode == Mode.BUFFERING) {
          acceptInBufferingMode(signal, transformationRequest);
        }
      }
    }
  }

  @VisibleForTesting
  void doFlush() {
    if (!buffer.isEmpty()) {
      log.trace("flushing buffer of size " + buffer.size());
      List<TransformationRequest> factsThatNeedTransformation =
          // filter the scheduled ones
          buffer.stream().map(Pair::left).filter(Objects::nonNull).toList();

      // resolve futures for the cache hits & transformations
      if (!factsThatNeedTransformation.isEmpty())
        CompletableFuture.runAsync(
            () -> {
              try {
                // it is important here not to do single lookups
                List<Fact> transformedFacts = service.transform(factsThatNeedTransformation);
                transformedFacts.forEach(
                    f -> {
                      CompletableFuture<Signal.FactSignal> futureSignalToComplete =
                          index.get(f.id());
                      if (futureSignalToComplete != null) {
                        futureSignalToComplete.complete(new Signal.FactSignal(f));
                      } else {
                        log.warn("found unexpected fact id after transformation: {}", f.id());
                      }
                    });
              } catch (Exception e) {
                // make all uncompleted fail
                index
                    .values()
                    .forEach(
                        cf -> {
                          if (!cf.isDone()) {
                            cf.completeExceptionally(e);
                          }
                        });
              }
            },
            es);

      // flush out, blocking where the fact is not yet available
      buffer.forEach(
          p -> {
            try {
              // all futures are completed by now
              Signal e = p.right().get();
              parent.process(e);
            } catch (InterruptedException i) {
              Thread.currentThread().interrupt();
              throw new TransformationException(i);
            } catch (ExecutionException e) {
              throw new TransformationException(e);
            }
          });

      // reset buffer
      buffer.clear();
      index.clear();
    }
  }
}
