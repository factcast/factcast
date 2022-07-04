package org.factcast.store.internal.catchup.fetching;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import org.apache.commons.lang3.tuple.Pair;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.core.subscription.transformation.TransformationRequest;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
/** this class is NOT Threadsafe! */
public class FetchBuffer {
  enum Mode {
    DIRECT,
    BUFFERING
  }

  private final FactTransformers transformers;
  private final SubscriptionImpl targetSubscription;
  private final int pageSize;
  private Mode mode = Mode.DIRECT;
  private final List<Pair<TransformationRequest, CompletableFuture<Fact>>> buffer =
      new ArrayList<>();
  private final Map<UUID, Pair<TransformationRequest, CompletableFuture<Fact>>> index =
      new HashMap<>();

  public void add(@NonNull Fact f) {
    TransformationRequest transformationRequest = transformers.prepareTransformation(f);
    if (mode == Mode.DIRECT) {
      if (transformationRequest == null) {
        // does not need transformation
        targetSubscription.notifyElement(f);
      } else {
        buffer.add(scheduledTransformation(transformationRequest));
        mode = Mode.BUFFERING;
      }
    } else if (mode == Mode.BUFFERING) {
      if (transformationRequest == null) {
        // does not need transformation
        buffer.add(completedTransformation(f));
      } else {
        Pair<TransformationRequest, CompletableFuture<Fact>> scheduledTransformation =
            scheduledTransformation(transformationRequest);
        buffer.add(scheduledTransformation);
        index.put(f.id(), scheduledTransformation);

        if (buffer.size() >= pageSize) flush();
      }
    }
  }

  @NonNull
  private Pair<TransformationRequest, CompletableFuture<Fact>> scheduledTransformation(
      @NonNull TransformationRequest transformationRequest) {
    return Pair.of(transformationRequest, null);
  }

  @NonNull
  private Pair<TransformationRequest, CompletableFuture<Fact>> completedTransformation(
      @NonNull Fact f) {
    return Pair.of(null, CompletableFuture.completedFuture(f));
  }

  public void flush() {
    List<TransformationRequest> factsThatNeedTransformation =
        index.values().stream()
            .map(Pair::getLeft)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    // resolve futures for the cache hits
    Map<UUID, Fact> cachedResults = fetchCachedResultsFor(factsThatNeedTransformation);
    cachedResults.forEach(
        (id, f) -> {
          index.get(id).setValue(CompletableFuture.completedFuture(f));
          index.remove(id);
        });

    // start async transformations, if any

    transformAndBatchWriteToCache(index);

    // flush out
    buffer.forEach(
        p -> {
          try {
            targetSubscription.notifyElement(p.getRight().get());
            // TODO how to handle those?
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          } catch (ExecutionException e) {
            throw new RuntimeException(e);
          }
        });

    // reset buffer
    buffer.clear();
    index.clear();
  }

  private void transformAndBatchWriteToCache(
      Map<UUID, Pair<TransformationRequest, CompletableFuture<Fact>>> index) {
    // should return immediately, so that the "flush out" step can start sending completed facts to
    // the consumer
    if (!index.isEmpty()) {
      CompletableFuture.runAsync(
          () -> {
            List<Pair<Fact, CompletableFuture<Fact>>> toWriteBack = new ArrayList<>();
            // iterate index and complete futures after transformation (in parallel)
            index.values().parallelStream()
                .forEach(
                    p -> {
                      // TODO
                      // toWriteBack.add(p);
                    });

            writeBackToTransformationCacheAsync(toWriteBack);
          });
    }
    ;
  }

  private void writeBackToTransformationCacheAsync(
      List<Pair<Fact, CompletableFuture<Fact>>> toWriteBack) {
    CompletableFuture.runAsync(
        () -> {
          // TODO
        });
  }

  @NonNull
  private Map<UUID, Fact> fetchCachedResultsFor(
      List<TransformationRequest> factsThatNeedTransformation) {
    // TODO
    return null;
  }
}
