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
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.observer.ServerSideFactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;

/**
 * FactObserver implementation, that translates observer Events to transport layer messages.
 *
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@Slf4j
class GrpcObserverAdapter implements ServerSideFactObserver {

  private final ProtoConverter converter = new ProtoConverter();

  @NonNull private final String id;

  @NonNull private final StreamObserver<MSG_Notification> observer;
  @NonNull private final ServerExceptionLogger serverExceptionLogger;

  @Getter(AccessLevel.PROTECTED)
  @VisibleForTesting
  private final ServerKeepalive keepalive;

  private final StagedFacts stagedFacts;
  private final boolean supportsFastForward;
  private final long keepaliveInMilliseconds;

  private final AtomicBoolean caughtUp = new AtomicBoolean(false);

  public GrpcObserverAdapter(
      @NonNull String id,
      @NonNull StreamObserver<MSG_Notification> observer,
      @NonNull GrpcRequestMetadata meta,
      @NonNull ServerExceptionLogger serverExceptionLogger,
      long keepaliveInMilliseconds) {
    this.id = id;
    this.observer = observer;
    supportsFastForward = meta.supportsFastForward();
    this.keepaliveInMilliseconds = keepaliveInMilliseconds;
    stagedFacts = new StagedFacts(meta.clientMaxInboundMessageSize());
    this.serverExceptionLogger = serverExceptionLogger;
    if (keepaliveInMilliseconds > 0) {
      keepalive = new ServerKeepalive();
    } else {
      keepalive = null;
    }
  }

  @VisibleForTesting
  GrpcObserverAdapter(
      @NonNull String id,
      @NonNull StreamObserver<MSG_Notification> observer,
      @NonNull ServerExceptionLogger serverExceptionLogger) {
    this(id, observer, GrpcRequestMetadata.forTest(), serverExceptionLogger, 0);
  }

  @VisibleForTesting
  @Deprecated
  GrpcObserverAdapter(@NonNull String id, @NonNull StreamObserver<MSG_Notification> observer) {
    this(id, observer, GrpcRequestMetadata.forTest(), new ServerExceptionLogger(), 0);
  }

  @VisibleForTesting
  GrpcObserverAdapter(
      @NonNull String id,
      @NonNull StreamObserver<MSG_Notification> observer,
      GrpcRequestMetadata meta) {
    this(id, observer, meta, new ServerExceptionLogger(), 0);
  }

  @VisibleForTesting
  GrpcObserverAdapter(
      @NonNull String id, @NonNull StreamObserver<MSG_Notification> observer, long keepalive) {
    this(id, observer, GrpcRequestMetadata.forTest(), new ServerExceptionLogger(), keepalive);
  }

  @VisibleForTesting
  GrpcObserverAdapter(
      @NonNull String id,
      @NonNull StreamObserver<MSG_Notification> observer,
      @NonNull GrpcRequestMetadata meta,
      @NonNull ServerExceptionLogger serverExceptionLogger) {
    this(id, observer, meta, serverExceptionLogger, 0);
  }

  @Override
  public void onComplete() {
    disableKeepalive();
    flush();
    log.debug("{} onComplete – sending complete notification", id);
    observer.onNext(converter.createCompleteNotification());
    tryComplete();
  }

  private void disableKeepalive() {
    if (keepalive != null) {
      keepalive.shutdown();
    }
  }

  @Override
  public void onError(@NonNull Throwable e) {
    disableKeepalive();
    flush();
    serverExceptionLogger.log(e, id);
    observer.onError(ServerExceptionHelper.translate(e));
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

  void flush() {
    // yes, it is threadsafe
    if (!stagedFacts.isEmpty()) {
      log.trace("{} flushing batch of {} facts", id, stagedFacts.size());
      observer.onNext(converter.createNotificationFor(stagedFacts.popAll()));
    }
  }

  @Override
  public void onNext(@NonNull List<Fact> element) {

    // might be an error?
    if (element.size() == 1) {
      log.error(
          "We're using batching on the server side, with a list size of one. While we will mitigate, for performance reasons this should be avoided. Please fix the code accordingly (see stsack trace below)",
          new BatchingOnServerSideShouldBeAvoidedException());
    }

    if (caughtUp.get()) {
      // TODO do not transfer immediately
      observer.onNext(converter.createNotificationFor(element));
    } else
      element.forEach(
          f -> {
            if (!stagedFacts.add(f)) {
              flush();
              // add it to the next batch
              stagedFacts.add(f);
            }
          });
  }

  public void onNext(@NonNull Fact f) {
    if (caughtUp.get()) {
      // TODO do not transfer immediately
      observer.onNext(converter.createNotificationFor(f));
    } else if (!stagedFacts.add(f)) {
      flush();
      // add it to the next batch
      stagedFacts.add(f);
    }
  }

  @Override
  public void onFastForward(@NonNull FactStreamPosition position) {
    if (supportsFastForward) {
      log.debug("{} sending ffwd notification to fact id {}", id, position);
      // we have not sent any fact. check for ffwding
      observer.onNext(converter.toProto(position));
    }
  }

  @Override
  public void onFactStreamInfo(FactStreamInfo info) {
    observer.onNext(converter.createInfoNotification(info));
  }

  public void shutdown() {
    disableKeepalive();
  }

  class ServerKeepalive {
    private Timer t;

    ServerKeepalive() {
      t = new Timer("server-keepalive-" + System.currentTimeMillis(), true);
      reschedule();
    }

    @VisibleForTesting
    synchronized void reschedule() {
      if (t != null) {
        t.schedule(
            new TimerTask() {
              @Override
              public void run() {
                observer.onNext(converter.createKeepaliveNotification());
                reschedule();
              }
            },
            keepaliveInMilliseconds);
      }
    }

    @VisibleForTesting
    synchronized void shutdown() {
      t.cancel();
    }
  }
}
