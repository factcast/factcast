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
package org.factcast.store.internal.filter;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
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
public class TransformingFactConsumer implements Consumer<Fact> {
  private final int maxBufferSize = 100; // just to not get too far ahead of ourselves

  public TransformingFactConsumer(
      @NonNull Consumer<Fact> parent,
      @NonNull FactTransformerService service,
      @NonNull FactTransformers transformers,
      @NonNull ExecutorService es) {
    this.service = service;
    this.transformers = transformers;
    this.parent = parent;
    buffer = new ArrayList<>(maxBufferSize);
    index = new HashMap<>(maxBufferSize);
    this.es = es;
  }

  enum Mode {
    DIRECT,
    BUFFERING
  }

  private final Consumer<Fact> parent;
  private final FactTransformerService service;
  private final FactTransformers transformers;
  private Mode mode = Mode.DIRECT;
  private final List<Pair<TransformationRequest, CompletableFuture<Fact>>> buffer;
  private final Map<UUID, CompletableFuture<Fact>> index;

  private final ExecutorService es;

  public void accept(@Nullable Fact f) {
    TransformationRequest transformationRequest = transformers.prepareTransformation(f);
    if (mode == Mode.DIRECT) {
      acceptInDirectMode(f, transformationRequest);
    } else {
      if (mode == Mode.BUFFERING) {
        acceptInBufferingMode(f, transformationRequest);
      }
    }
  }

  private void acceptInBufferingMode(
      @Nullable Fact f, TransformationRequest transformationRequest) {
    if (transformationRequest == null) {
      // does not need transformation, add as completed
      this.buffer.add(completedTransformation(f));

      flushIfNecessary();
    } else {
      Pair<TransformationRequest, CompletableFuture<Fact>> scheduledTransformation =
          scheduledTransformation(transformationRequest);
      addScheduledTransformationToBuffer(scheduledTransformation);
    }
  }

  private void flushIfNecessary() {
    if (buffer.size() >= maxBufferSize) {
      flush();
    }
  }

  private void addScheduledTransformationToBuffer(
      Pair<TransformationRequest, CompletableFuture<Fact>> scheduledTransformation) {
    buffer.add(scheduledTransformation);
    index.put(scheduledTransformation.left().toTransform().id(), scheduledTransformation.right());

    flushIfNecessary();
  }

  private void acceptInDirectMode(@Nullable Fact f, TransformationRequest transformationRequest) {
    if (transformationRequest == null) {
      // does not need transformation, just pass it down
      parent.accept(f);

    } else {
      // needs transformation, so switch to buffering mode
      mode = Mode.BUFFERING;
      addScheduledTransformationToBuffer(scheduledTransformation(transformationRequest));
    }
  }

  @NonNull
  private Pair<TransformationRequest, CompletableFuture<Fact>> scheduledTransformation(
      @NonNull TransformationRequest transformationRequest) {
    return Pair.of(transformationRequest, new CompletableFuture<>());
  }

  @NonNull
  private Pair<TransformationRequest, CompletableFuture<Fact>> completedTransformation(Fact f) {
    return Pair.of(null, CompletableFuture.completedFuture(f));
  }

  public void flush() {
    if (!buffer.isEmpty()) {
      log.trace("flushing buffer of size " + buffer.size());
      List<TransformationRequest> factsThatNeedTransformation =
          // filter the scheduled ones
          buffer.stream().map(Pair::left).filter(Objects::nonNull).collect(Collectors.toList());

      // resolve futures for the cache hits & transformations
      if (!factsThatNeedTransformation.isEmpty())
        CompletableFuture.runAsync(
            () -> {
              try {
                List<Fact> transformedFacts = service.transform(factsThatNeedTransformation);
                transformedFacts.forEach(
                    f -> {
                      CompletableFuture<Fact> factCompletableFuture = index.get(f.id());
                      if (factCompletableFuture != null) {
                        factCompletableFuture.complete(f);
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
              // 30 seconds should be enough for almost everything (B.Gates)
              Fact e = p.right().get(30, TimeUnit.SECONDS);
              parent.accept(e);
            } catch (InterruptedException i) {
              Thread.currentThread().interrupt();
              throw new TransformationException(i);
            } catch (ExecutionException | TimeoutException e) {
              throw new TransformationException(e);
            }
          });

      // use null as special value to signal that output should be flushed
      parent.accept(null);

      // reset buffer
      buffer.clear();
      index.clear();
    }
  }
}
