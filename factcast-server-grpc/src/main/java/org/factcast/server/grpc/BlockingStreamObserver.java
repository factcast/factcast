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
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * StreamObserver impl that blocks if the Stream to the consumer is not in writeable state to
 * provide a basic backpressure alike property.
 *
 * <p>Note it the consumer stream is not writeable, the {@link BlockingStreamObserver} will retry
 * RETRY_COUNT (default 60) times after WAIT_TIME (default 1000) millis
 *
 * @param <T>
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@Slf4j
public class BlockingStreamObserver<T> implements StreamObserver<T> {

  private static final int RETRY_COUNT = 100;

  private static final long WAIT_TIME_MILLIS = 1000;

  private final ServerCallStreamObserver<T> delegate;
  private int batchSize;

  private final Object lock = new Object();

  private final String id;

  BlockingStreamObserver(
      @NonNull String id, @NonNull ServerCallStreamObserver<T> delegate, int batchSize) {
    if (batchSize < 1) throw new IllegalArgumentException("batchSize must be >=1");
    this.id = id;
    this.delegate = delegate;
    this.batchSize = batchSize;
    this.delegate.setOnReadyHandler(this::wakeup);
    this.delegate.setOnCancelHandler(this::wakeup);
  }

  @VisibleForTesting
  void wakeup() {
    synchronized (lock) {
      // wake up our thread
      lock.notifyAll();
    }
  }

  @Override
  public void onNext(T value) {
    if (!isCancelled(delegate)) {
      synchronized (lock) {
        if (!delegate.isReady()) {
          // delegate is not ready, lets be patient
          waitForDelegate();

          if (isCancelled(delegate)) {
            return;
          } else {
            if (!delegate.isReady()) {
              throw new TransportLayerException(
                  id
                      + " channel not coming back after waiting "
                      + (WAIT_TIME_MILLIS * batchSize * RETRY_COUNT)
                      + "msec ("
                      + WAIT_TIME_MILLIS
                      + " * batchSize * "
                      + RETRY_COUNT
                      + " retries");
            }
          }
        }

        // what about now?
        if (!isCancelled(delegate)) {
          if (delegate.isReady()) {
            delegate.onNext(value);
          } else {

            // no longer ready. has it been canceled in the meantime?
            if (!isCancelled(delegate)) {
              // still not (or no longer) ready, but not canceled... something fishy is going on
              // here.
              throw new TransportLayerException(id + " channel not coming back.");
            } else {
              // ok fine, it was cancelled, this fact was logged, we can quietly exit...
              return;
            }
          }
        }
      }
    }
  }

  private boolean isCancelled(ServerCallStreamObserver<T> delegate) {
    if (delegate.isCancelled()) {
      log.debug("{} channel cancelled by the client", id);
      return true;
    } else {
      return false;
    }
  }

  @VisibleForTesting
  void waitForDelegate() {
    int retry = RETRY_COUNT * batchSize;
    for (int i = 1; i <= retry; i++) {

      log.trace("{} channel not ready. Slow client? Attempt: {}/{}", id, i, retry);
      try {
        lock.wait(WAIT_TIME_MILLIS);
      } catch (InterruptedException meh) {
        // ignore
      }

      // if something changed in the meantime
      if (delegate.isReady() || delegate.isCancelled()) {
        break;
      }
    }
  }

  @Override
  public void onError(Throwable t) {

    if (t instanceof StatusRuntimeException) {
      // prevent double wrap
      delegate.onError(t);
    } else {
      delegate.onError(ServerExceptionHelper.translate(t));
    }
  }

  @Override
  public void onCompleted() {
    delegate.onCompleted();
  }
}
