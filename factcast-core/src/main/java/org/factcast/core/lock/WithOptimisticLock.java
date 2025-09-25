/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.core.lock;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;

@Slf4j
@RequiredArgsConstructor
@Accessors(fluent = true, chain = true)
public class WithOptimisticLock {
  @NonNull
  @Getter(value = AccessLevel.PROTECTED)
  private final FactStore store;

  @NonNull
  @Getter(value = AccessLevel.PROTECTED)
  private final List<FactSpec> factSpecs;

  @Setter private int retry = 10;

  @Setter private long interval = 0;

  private int count = 0;

  private static final LoadingCache<String, ReadWriteLock> cache =
      CacheBuilder.newBuilder()
          // to make sure we do not flood our memory with agg ids. if performance is an issue,
          // get two caches, one for namespaces and one for namespaces with agg ids
          .expireAfterAccess(Duration.ofMinutes(1))
          .maximumSize(50_000)
          .build(CacheLoader.from(ns -> new ReentrantReadWriteLock()));
  private static final ReadWriteLock generalPublishLock = new ReentrantReadWriteLock(true);

  @NonNull
  @SneakyThrows
  public PublishingResult attempt(@NonNull Attempt operation)
      throws AttemptAbortedException, OptimisticRetriesExceededException, ExceptionAfterPublish {

    Set<String> readNs = factSpecs.stream().flatMap(this::expand).collect(Collectors.toSet());
    boolean containsStar = readNs.contains("*");
    Set<String> readNsFiltered =
        readNs.stream().filter(ns -> !ns.equals("*")).collect(Collectors.toSet());

    Set<String> writeNs =
        factSpecs.stream()
            .flatMap(this::expand)
            .filter(ns -> !ns.equals("*"))
            .collect(Collectors.toSet());

    List<String> nsMerged =
        Sets.union(readNsFiltered, writeNs).stream().sorted().collect(Collectors.toList());

    while (++count <= retry) {

      boolean publishIfUnchanged = false;

      Set<Lock> obtainedLocks = new HashSet<>();
      try {

        if (containsStar) {
          Lock actualLock = generalPublishLock.writeLock();
          obtainedLocks.add(actualLock);
          actualLock.lock();
        } else {
          Lock actualLock = generalPublishLock.readLock();
          obtainedLocks.add(actualLock);
          actualLock.lock();
        }

        for (String ns : nsMerged) {
          ReadWriteLock lock = cache.get(ns);
          if (writeNs.contains(ns) && ns.contains("::")) {
            Lock actualLock = lock.writeLock();
            obtainedLocks.add(actualLock);
            actualLock.lock();
          } else {
            Lock actualLock = lock.readLock();
            obtainedLocks.add(actualLock);
            actualLock.lock();
          }
        }

        // fetch current state
        StateToken token = store.currentStateFor(factSpecs);

        try {

          // execute the business logic
          // in case an AttemptAbortedException is thrown, just pass it
          // through
          IntermediatePublishResult r = runAndWrapException(operation);
          if (r.wasSkipped()) {
            List<Fact> facts = Collections.emptyList();
            executeAndThen(r, facts);
            return new PublishingResult(facts);
          }

          List<Fact> factsToPublish = r.factsToPublish();
          if (factsToPublish == null || factsToPublish.isEmpty()) {
            throw new IllegalArgumentException(
                "Attempt exited without abort, but does not publish any facts.");
          }

          // from here on, we know the server takes care of invalidation
          publishIfUnchanged = true;

          // try to publish
          if (store.publishIfUnchanged(r.factsToPublish(), Optional.of(token))) {

            executeAndThen(r, factsToPublish);

            // and return the lastFactId for reference
            return new PublishingResult(factsToPublish);

          } else {
            sleep();
          }
        } finally {
          if (!publishIfUnchanged) {
            store.invalidate(token);
          }
        }
      } finally {
        obtainedLocks.forEach(
            lock -> {
              try {
                lock.unlock();
              } catch (Exception e) {
                //
              }
            });
      }
    }

    throw new OptimisticRetriesExceededException(retry);
  }

  private Stream<String> expand(FactSpec factSpec) {
    if (factSpec.ns().equals("*") || factSpec.aggIds().isEmpty()) {
      return Stream.of(factSpec.ns());
    }

    return Stream.concat(
        Stream.of(factSpec.ns()), factSpec.aggIds().stream().map(id -> factSpec.ns() + "::" + id));
  }

  private static void executeAndThen(IntermediatePublishResult r, List<Fact> factsToPublish) {
    // publishing worked
    // now run the 'andThen' operation
    try {
      r.andThen().ifPresent(Runnable::run);
    } catch (Throwable e) {
      throw new ExceptionAfterPublish(factsToPublish, e);
    }
  }

  private IntermediatePublishResult runAndWrapException(Attempt operation)
      throws AttemptAbortedException {

    try {
      IntermediatePublishResult ret = operation.call();
      if (ret == null) {
        // Attempt should not return null, this is an abuse of the API.
        log.error(
            "Attempt should not return null, this is an abuse of the API. We will however treat it"
                + " as an abort. Please fix the problem!");
        throw new AttemptAbortedException("Attempt aborted due to null-return. No message given.");
      }
      return ret;
    } catch (Exception e) {
      if (!AttemptAbortedException.class.isAssignableFrom(e.getClass())) {
        throw new AttemptAbortedException(e);
      } else {
        throw e;
      }
    }
  }

  @SneakyThrows
  private void sleep() {
    if (interval > 0) {
      Thread.sleep(interval);
    }
  }

  @Getter
  public static final class OptimisticRetriesExceededException
      extends ConcurrentModificationException {

    private static final long serialVersionUID = 1L;

    private final int retries;

    public OptimisticRetriesExceededException(int retry) {
      super("Exceeded the maximum number of retrys allowed (" + retry + ")");
      retries = retry;
    }
  }
}
