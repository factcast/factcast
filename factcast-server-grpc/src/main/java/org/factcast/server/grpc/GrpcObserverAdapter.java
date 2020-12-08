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
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;

/**
 * FactObserver implementation, that translates observer Events to transport layer messages.
 *
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Slf4j
@RequiredArgsConstructor
class GrpcObserverAdapter implements FactObserver {

  final ProtoConverter converter = new ProtoConverter();

  @NonNull final String id;

  @NonNull final StreamObserver<MSG_Notification> observer;
  @NonNull final int catchupBatchSize;

  @VisibleForTesting
  GrpcObserverAdapter(String id, StreamObserver<MSG_Notification> observer) {
    this(id, observer, 1);
  }

  private final ArrayList<Fact> stagedFacts = new ArrayList<Fact>();
  private final AtomicBoolean caughtUp = new AtomicBoolean(false);

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
    if (catchupBatchSize > 1 && !caughtUp.get()) {
      if (stagedFacts.size() >= catchupBatchSize) flush();
      stagedFacts.add(element);
    } else observer.onNext(converter.createNotificationFor(element));
  }
}
