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
package org.factcast.client.grpc;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.InternalSubscription;
import org.factcast.core.subscription.StaleSubscriptionDetectedException;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;

/**
 * Bridges GRPC Specific StreamObserver to a subscription by switching over the notification type
 * and dispatching to the appropriate subscription method.
 *
 * @author <uwe.schaefer@prisma-capacity.eu>
 * @see StreamObserver
 * @see Subscription
 */
@Slf4j
class ClientStreamObserver implements StreamObserver<FactStoreProto.MSG_Notification> {

  private final ProtoConverter converter = new ProtoConverter();
  private final AtomicLong lastNotification = new AtomicLong(0);

  @Getter(AccessLevel.PROTECTED)
  private final ExecutorService clientBoundExecutor = Executors.newSingleThreadExecutor();

  @NonNull private final InternalSubscription subscription;

  @VisibleForTesting
  @Getter(AccessLevel.PACKAGE)
  private final ClientKeepalive keepAlive;

  public ClientStreamObserver(@NonNull InternalSubscription subscription, long keepAliveInterval) {
    this.subscription = subscription;

    if (keepAliveInterval != 0L) {
      keepAlive = new ClientKeepalive(keepAliveInterval);
    } else {
      keepAlive = null;
    }

    subscription.onClose(this::tryShutdown);
    subscription.onClose(this::disableKeepalive);
  }

  @VisibleForTesting
  void tryShutdown() {
    try {
      clientBoundExecutor.shutdown();
    } catch (Exception e) {
      log.error("While shutting down executor:", e);
    }
  }

  @Override
  public void onNext(MSG_Notification f) {
    lastNotification.set(System.currentTimeMillis());

    try {
      if (clientBoundExecutor.isShutdown())
        throw new IllegalStateException(
            "Executor for this observer already shut down. THIS IS A BUG!");

      clientBoundExecutor.submit(() -> process(f)).get();
    } catch (ExecutionException e) {
      tryShutdown();
      throw ExceptionHelper.toRuntime(e.getCause());
    } catch (InterruptedException e) {
      tryShutdown();
      Thread.currentThread().interrupt();
    }
  }

  private void process(MSG_Notification f) {
    switch (f.getType()) {
      case Info:
        log.trace("received info signal");
        // receive info message once at the very beginning of the stream
        FactStreamInfo factStreamInfo = converter.fromProto(f.getInfo());
        subscription.notifyFactStreamInfo(factStreamInfo);
        break;
      case KeepAlive:
        log.trace("received keepalive signal");
        // NOP, just used for the update of lastNotification
        break;
      case Catchup:
        log.trace("received onCatchup signal");
        subscription.notifyCatchup();
        break;
      case Complete:
        log.trace("received onComplete signal");
        onCompleted();
        break;
      case Fact:
        log.trace("received single fact");
        subscription.notifyElement(converter.fromProto(f.getFact()));
        break;
      case Facts:
        List<? extends Fact> facts = converter.fromProto(f.getFacts());
        log.trace("received {} facts", facts.size());

        for (Fact fact : facts) {
          subscription.notifyElement(fact);
        }
        break;
      case Ffwd:
        log.debug("received fastforward signal");
        // if the server is <0.7.5, the fwd notification does not contain a serial. In this case, we
        // just skip it.
        FactStreamPosition pos = converter.fromProto(f.getId(), f.getSerial());
        if (pos.serial() != 0L) {
          subscription.notifyFastForward(pos);
        } else {
          log.warn(
              "Server sent FFWD without serial information. Either downgrade the client or upgrade the server (to 0.7.5+) in order for FFWD to be enabled.");
        }
        break;

      default:
        onError(new RuntimeException("Unrecognized notification type. THIS IS A BUG!"));
        break;
    }
  }

  @VisibleForTesting
  void disableKeepalive() {
    if (keepAlive != null) {
      keepAlive.shutdown();
    }
  }

  @Override
  public void onError(Throwable t) {
    disableKeepalive();
    RuntimeException translated = ClientExceptionHelper.from(t);
    try {
      subscription.notifyError(translated);
    } finally {
      tryShutdown();
    }
  }

  @Override
  public void onCompleted() {
    disableKeepalive();
    try {
      subscription.notifyComplete();
    } finally {
      tryShutdown();
    }
  }

  class ClientKeepalive {
    private final Timer t = new Timer("client-keepalive-" + System.currentTimeMillis(), true);
    private final long interval;
    private final long gracePeriod;

    ClientKeepalive(long interval) {
      this.interval = interval;
      gracePeriod =
          interval * 2 + 200; // 2 times the interval and 200ms extra for potential network i/o
      lastNotification.set(System.currentTimeMillis());
      reschedule();
    }

    void reschedule() {
      t.schedule(
          new TimerTask() {
            @Override
            public void run() {
              long last = lastNotification.get();

              if (System.currentTimeMillis() - last > gracePeriod) {
                onError(new StaleSubscriptionDetectedException(last, gracePeriod));
              } else {
                reschedule();
              }
            }
          },
          interval);
    }

    void shutdown() {
      t.cancel(); // the timer and related threads altogether
    }
  }
}
