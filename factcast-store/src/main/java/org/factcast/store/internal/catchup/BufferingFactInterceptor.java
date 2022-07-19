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
package org.factcast.store.internal.catchup;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.store.internal.AbstractFactInterceptor;
import org.factcast.store.internal.Pair;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.filter.FactFilter;

/**
 * this class is NOT Threadsafe!
 */
@Slf4j
public class BufferingFactInterceptor extends AbstractFactInterceptor {
  public BufferingFactInterceptor(
          FactTransformerService service,
          FactTransformers transformers,
          FactFilter filter,
          SubscriptionImpl targetSubscription,
          int maxBufferSize,
          PgMetrics metrics
  ) {
    super(metrics);
    this.service = service;
    this.transformers = transformers;
    this.filter = filter;
    this.targetSubscription = targetSubscription;
    this.maxBufferSize = maxBufferSize;
  }

  enum Mode {
    DIRECT,
    BUFFERING
  }

  private final FactTransformerService service;
  private final FactTransformers transformers;
  private final FactFilter filter;
  private final SubscriptionImpl targetSubscription;
  private final int maxBufferSize;
  private Mode mode = Mode.DIRECT;
  private final List<Pair<TransformationRequest, CompletableFuture<Fact>>> buffer =
          new ArrayList<>();
  private final Map<UUID, CompletableFuture<Fact>> index = new HashMap<>();

  public void accept(@NonNull Fact f) {
    if (filter.test(f)) {

      TransformationRequest transformationRequest = transformers.prepareTransformation(f);
      if (mode == Mode.DIRECT) {
        acceptInDirectMode(f, transformationRequest);
      }
      else {
        if (mode == Mode.BUFFERING) {
          acceptInBufferingMode(f, transformationRequest);
        }
      }
    }
  }

  private void acceptInBufferingMode(@NonNull Fact f, TransformationRequest transformationRequest) {
    log.trace("accepting in buffering mode");
    if (transformationRequest == null) {
      // does not need transformation, add as completed
      buffer.add(completedTransformation(f));
    }
    else {
      Pair<TransformationRequest, CompletableFuture<Fact>> scheduledTransformation =
              scheduledTransformation(transformationRequest);
      addScheduledTransformationToBuffer(scheduledTransformation);
    }
  }

  private void addScheduledTransformationToBuffer(
          Pair<TransformationRequest, CompletableFuture<Fact>> scheduledTransformation
  ) {
    buffer.add(scheduledTransformation);
    index.put(scheduledTransformation.left().toTransform().id(), scheduledTransformation.right());

    if (buffer.size() >= maxBufferSize) {
      flush();
    }
  }

  private void acceptInDirectMode(@NonNull Fact f, TransformationRequest transformationRequest) {
    log.trace("accepting in direct mode");
    if (transformationRequest == null) {
      // does not need transformation, just pass it down
      targetSubscription.notifyElement(f);
      increaseNotifyMetric(1);
    }
    else {
      // needs transformation, so switch to buffering mode
      mode = Mode.BUFFERING;
      addScheduledTransformationToBuffer(scheduledTransformation(transformationRequest));
    }
  }

  @NonNull
  private Pair<TransformationRequest, CompletableFuture<Fact>> scheduledTransformation(
          @NonNull TransformationRequest transformationRequest
  ) {
    return Pair.of(transformationRequest, new CompletableFuture<>());
  }

  @NonNull
  private Pair<TransformationRequest, CompletableFuture<Fact>> completedTransformation(
          @NonNull Fact f
  ) {
    return Pair.of(null, CompletableFuture.completedFuture(f));
  }

  public void flush() {
    log.trace("flushing buffer of size " + buffer.size());
    List<TransformationRequest> factsThatNeedTransformation =
            // filter the scheduled ones
            buffer.stream().map(Pair::left).filter(Objects::nonNull).collect(Collectors.toList());

    // resolve futures for the cache hits & transformations
    CompletableFuture<Void> voidCompletableFuture =
            CompletableFuture.runAsync(
                    () -> {
                      try {
                        List<Fact> transformedFacts = service.transform(factsThatNeedTransformation);
                        transformedFacts.forEach(
                                f -> {
                                  CompletableFuture<Fact> factCompletableFuture = index.get(f.id());
                                  if (factCompletableFuture != null) {
                                    factCompletableFuture.complete(f);
                                  }
                                  else {
                                    log.warn("found unexpected fact id after transformation: {}", f.id());
                                  }
                                });
                      } catch (Exception e) {
                        // make all uncompleted fail
                        index.values().forEach(cf -> {
                          if (!cf.isDone()) {
                            cf.completeExceptionally(e);
                          }
                        });
                      }
                    });

    // flush out, blocking where the fact is not yet available
    buffer.forEach(
            p -> {
              try {
                // 30 seconds should be enough for almost everything (B.Gates)
                Fact e = p.right().get(30, TimeUnit.SECONDS);
                targetSubscription.notifyElement(e);
              } catch (InterruptedException i) {
                Thread.currentThread().interrupt();
                throw new TransformationException(i);
              } catch (ExecutionException | TimeoutException e) {
                throw new TransformationException(e);
              }
            });

    increaseNotifyMetric(buffer.size());

    // reset buffer
    buffer.clear();
    index.clear();
  }
}
