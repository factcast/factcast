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
package org.factcast.server.grpc;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;

/**
 * FactObserver implementation, that translates observer Events to transport layer messages.
 *
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@Slf4j
class GrpcObserverAdapter implements FactObserver {

  private final ProtoConverter converter = new ProtoConverter();

  @NonNull private final String id;

  @NonNull private final StreamObserver<MSG_Notification> observer;
  @NonNull private final int catchupBatchSize;
  private boolean factsApplied = false;

  @VisibleForTesting
  @Deprecated
  GrpcObserverAdapter(String id, StreamObserver<MSG_Notification> observer) {
    this(id, observer, GrpcRequestMetadata.forTest(), 0, FastForwardTarget.forTest());
  }

  private final ArrayList<Fact> stagedFacts;
  private final Long startingAfterSerialOrNull;
  private final FastForwardTarget ffwdTarget;
  private final boolean supportsFastForward;
  private final AtomicBoolean caughtUp = new AtomicBoolean(false);

  public GrpcObserverAdapter(
      @NonNull String id,
      @NonNull StreamObserver<MSG_Notification> observer,
      @NonNull GrpcRequestMetadata meta,
      long startingAfterSerialOrNull,
      @NonNull FastForwardTarget ffwdTarget) {
    this.id = id;
    this.observer = observer;
    catchupBatchSize = meta.catchupBatch().orElse(1);
    supportsFastForward = meta.supportsFastForward();
    this.startingAfterSerialOrNull = startingAfterSerialOrNull;
    this.ffwdTarget = ffwdTarget;
    stagedFacts = new ArrayList<>(catchupBatchSize);
  }

  @Override
  public void onComplete() {
    flush();
    log.debug("{} onComplete – sending complete notification", id);
    observer.onNext(converter.createCompleteNotification());
    tryComplete();
  }

  @Override
  public void onError(Throwable e) {
    flush();
    log.info("{} onError – sending Error notification {}", id, e.getMessage());
    observer.onError(e);
    tryComplete();
  }

  private void tryComplete() {
    try {
      observer.onCompleted();
    } catch (Throwable e) {
      log.trace("{} Expected exception on completion {}", id, e.getMessage());
    }
  }

  @Override
  public void onCatchup() {
    flush();

    if (supportsFastForward && !factsApplied) {
      // we have not sent any fact. check for ffwding

      UUID targetId = ffwdTarget.targetId();
      long targetSer = ffwdTarget.targetSer();

      if (targetId != null
          && (startingAfterSerialOrNull == null // we started from scratch
              || targetSer > startingAfterSerialOrNull // we are possibly outside of the tail index
          )) {
        log.debug("{} no facts applied – sending ffwd notification to fact id {}", id, targetId);
        observer.onNext(converter.createNotificationForFastForward(targetId));
      }
    }

    log.debug("{} onCatchup – sending catchup notification", id);
    observer.onNext(converter.createCatchupNotification());
    caughtUp.set(true);
  }

  private void flush() {
    // yes, it is threadsafe
    if (!stagedFacts.isEmpty()) {
      log.trace("{} flushing batch of {} facts", id, stagedFacts.size());
      observer.onNext(converter.createNotificationFor(stagedFacts));
      stagedFacts.clear();
    }
  }

  @Override
  public void onNext(Fact element) {
    factsApplied = true;
    if (catchupBatchSize > 1 && !caughtUp.get()) {
      if (stagedFacts.size() >= catchupBatchSize) {
        flush();
      }
      stagedFacts.add(element);
    } else {
      observer.onNext(converter.createNotificationFor(element));
    }
  }
}
