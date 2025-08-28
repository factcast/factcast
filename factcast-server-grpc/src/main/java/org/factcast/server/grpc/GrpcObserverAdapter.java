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

import static org.factcast.server.grpc.metrics.ServerMetrics.EVENT.BYTES_SENT;
import static org.factcast.server.grpc.metrics.ServerMetrics.EVENT.FACTS_SENT;
import static org.factcast.server.grpc.metrics.ServerMetrics.TAG_CLIENT_ID_KEY;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Tags;
import java.util.Timer;
import java.util.TimerTask;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.server.grpc.metrics.NOPServerMetrics;
import org.factcast.server.grpc.metrics.ServerMetrics;

/**
 * FactObserver implementation, that translates observer Events to transport layer messages.
 *
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@Slf4j
class GrpcObserverAdapter implements FactObserver {

  private final ProtoConverter converter = new ProtoConverter();

  @NonNull private final String id;

  @NonNull private final StreamObserver<MSG_Notification> notificationStreamObserver;
  @NonNull private final ServerExceptionLogger serverExceptionLogger;

  @NonNull private final ServerMetrics serverMetrics;
  @NonNull private final Tags metricTags;

  @Getter(AccessLevel.PROTECTED)
  @VisibleForTesting
  private final ServerKeepalive keepalive;

  private final StagedFacts stagedFacts;
  private final boolean supportsFastForward;
  private final long keepaliveInMilliseconds;

  public GrpcObserverAdapter(
      @NonNull String id,
      @NonNull StreamObserver<MSG_Notification> observer,
      @NonNull GrpcRequestMetadata meta,
      @NonNull ServerExceptionLogger serverExceptionLogger,
      @NonNull ServerMetrics serverMetrics,
      long keepaliveInMilliseconds) {
    this.id = id;
    this.notificationStreamObserver = observer;
    supportsFastForward = meta.supportsFastForward();
    this.keepaliveInMilliseconds = keepaliveInMilliseconds;
    stagedFacts = new StagedFacts(meta.clientMaxInboundMessageSize());
    this.serverExceptionLogger = serverExceptionLogger;
    if (keepaliveInMilliseconds > 0) {
      keepalive = new ServerKeepalive();
    } else {
      keepalive = null;
    }
    this.serverMetrics = serverMetrics;
    this.metricTags = Tags.of(TAG_CLIENT_ID_KEY, meta.clientIdAsString());
  }

  @VisibleForTesting
  GrpcObserverAdapter(
      @NonNull String id,
      @NonNull StreamObserver<MSG_Notification> observer,
      @NonNull ServerExceptionLogger serverExceptionLogger) {
    this(
        id,
        observer,
        GrpcRequestMetadata.forTest(),
        serverExceptionLogger,
        new NOPServerMetrics(),
        0);
  }

  @VisibleForTesting
  @Deprecated
  GrpcObserverAdapter(@NonNull String id, @NonNull StreamObserver<MSG_Notification> observer) {
    this(
        id,
        observer,
        GrpcRequestMetadata.forTest(),
        new ServerExceptionLogger(),
        new NOPServerMetrics(),
        0);
  }

  @VisibleForTesting
  GrpcObserverAdapter(
      @NonNull String id,
      @NonNull StreamObserver<MSG_Notification> observer,
      GrpcRequestMetadata meta) {
    this(id, observer, meta, new ServerExceptionLogger(), new NOPServerMetrics(), 0);
  }

  @VisibleForTesting
  GrpcObserverAdapter(
      @NonNull String id, @NonNull StreamObserver<MSG_Notification> observer, long keepalive) {
    this(
        id,
        observer,
        GrpcRequestMetadata.forTest(),
        new ServerExceptionLogger(),
        new NOPServerMetrics(),
        keepalive);
  }

  @VisibleForTesting
  GrpcObserverAdapter(
      @NonNull String id,
      @NonNull StreamObserver<MSG_Notification> observer,
      @NonNull GrpcRequestMetadata meta,
      @NonNull ServerExceptionLogger serverExceptionLogger) {
    this(id, observer, meta, serverExceptionLogger, new NOPServerMetrics(), 0);
  }

  @Override
  public void onComplete() {
    disableKeepalive();
    flush();
    log.debug("{} onComplete – sending complete notification", id);
    notificationStreamObserver.onNext(converter.createCompleteNotification());
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
    notificationStreamObserver.onError(ServerExceptionHelper.translate(e));
  }

  private void tryComplete() {
    try {
      notificationStreamObserver.onCompleted();
    } catch (Throwable e) {
      log.trace("{} Expected exception on completion {}", id, e.getMessage());
    }
  }

  @Override
  public void onCatchup() {
    flush();
    log.debug("{} onCatchup – sending catchup notification", id);
    notificationStreamObserver.onNext(converter.createCatchupNotification());
  }

  @Override
  public void flush() {
    // yes, it is used in a threadsafe manner
    if (!stagedFacts.isEmpty()) {
      log.trace("{} flushing batch of {} facts", id, stagedFacts.size());

      serverMetrics.count(BYTES_SENT, metricTags, stagedFacts.currentBytes());
      serverMetrics.count(FACTS_SENT, metricTags, stagedFacts.size());

      notificationStreamObserver.onNext(converter.createNotificationFor(stagedFacts.popAll()));
    }
  }

  public void onNext(@NonNull Fact f) {
    if (!stagedFacts.add(f)) {
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
      notificationStreamObserver.onNext(converter.toProto(position));
    }
  }

  @Override
  public void onFactStreamInfo(FactStreamInfo info) {
    notificationStreamObserver.onNext(converter.createInfoNotification(info));
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
                notificationStreamObserver.onNext(converter.createKeepaliveNotification());
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
