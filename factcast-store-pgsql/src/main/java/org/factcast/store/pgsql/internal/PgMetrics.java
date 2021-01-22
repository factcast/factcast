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
package org.factcast.store.pgsql.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.pgsql.internal.PgMetrics.StoreMetrics.OP;

@Slf4j
public class PgMetrics {

  @NonNull private final MeterRegistry registry;

  public PgMetrics(@NonNull MeterRegistry registry) {
    this.registry = registry;

    /*
     * Register all non-exceptional meters, so that an operational dashboard
     * can visualize all possible operations dynamically without hardcoding
     * them.
     */
    for (OP op : OP.values()) {
      timer(op, StoreMetrics.TAG_EXCEPTION_VALUE_NONE);
    }
  }

  @NonNull
  public Counter counter(@NonNull OP operation) {
    Tags tags = forOperation(operation, StoreMetrics.TAG_EXCEPTION_VALUE_NONE);
    // ommitting the meter description here
    return Counter.builder(StoreMetrics.COUNTER_METRIC_NAME).tags(tags).register(registry);
  }

  private Tags forOperation(@NonNull OP operation, @NonNull String exceptionTagValue) {
    return Tags.of(
        Tag.of(StoreMetrics.TAG_STORE_KEY, StoreMetrics.TAG_STORE_VALUE),
        Tag.of(StoreMetrics.TAG_OPERATION_KEY, operation.op()),
        Tag.of(StoreMetrics.TAG_EXCEPTION_KEY, exceptionTagValue));
  }

  public void time(@NonNull OP operation, @NonNull Runnable r) {
    Sample sample = Timer.start();
    Exception exception = null;
    try {
      r.run();
    } catch (Exception e) {
      exception = e;
      throw e;
    } finally {
      time(operation, sample, exception);
    }
  }

  public <T> T time(@NonNull OP operation, @NonNull Supplier<T> s) {
    Sample sample = Timer.start();
    Exception exception = null;
    try {
      return s.get();
    } catch (Exception e) {
      exception = e;
      throw e;
    } finally {
      time(operation, sample, exception);
    }
  }

  private void time(@NonNull OP operation, @NonNull Sample sample, Exception e) {
    try {
      String exceptionTagValue = mapException(e);
      sample.stop(timer(operation, exceptionTagValue));
    } catch (Exception exception) {
      log.warn("Failed timing operation!", exception);
    }
  }

  @NonNull
  private static String mapException(Exception e) {
    if (e == null) {
      return StoreMetrics.TAG_EXCEPTION_VALUE_NONE;
    }
    return e.getClass().getSimpleName();
  }

  @NonNull
  private Timer timer(@NonNull OP operation, @NonNull String exceptionTagValue) {
    Tags tags = forOperation(operation, exceptionTagValue);
    return Timer.builder(StoreMetrics.DURATION_METRIC_NAME).tags(tags).register(registry);
  }

  @NonNull
  public Timer timer(@NonNull OP operation) {
    return timer(operation, StoreMetrics.TAG_EXCEPTION_VALUE_NONE);
  }

  public static class StoreMetrics {

    static final String DURATION_METRIC_NAME = "factcast.store.operations.duration";

    static final String COUNTER_METRIC_NAME = "factcast.store.operations";

    static final String TAG_STORE_KEY = "store";

    static final String TAG_STORE_VALUE = "pgsql";

    static final String TAG_OPERATION_KEY = "operation";

    static final String TAG_EXCEPTION_KEY = "exception";

    static final String TAG_EXCEPTION_VALUE_NONE = "None";

    public enum OP {
      PUBLISH("publish"),

      SUBSCRIBE_FOLLOW("subscribe-follow"),

      SUBSCRIBE_CATCHUP("subscribe-catchup"),

      FETCH_BY_ID("fetchById"),

      SERIAL_OF("serialOf"),

      ENUMERATE_NAMESPACES("enumerateNamespaces"),

      ENUMERATE_TYPES("enumerateTypes"),

      GET_STATE_FOR("getStateFor"),

      PUBLISH_IF_UNCHANGED("publishIfUnchanged"),

      GET_SNAPSHOT("getSnapshot"),

      SET_SNAPSHOT("setSnapshot"),

      CLEAR_SNAPSHOT("clearSnapshot"),

      COMPACT_SNAPSHOT_CACHE("compactSnapshotCache"),

      NOTIFY_ROUNDTRIP_LATENCY("notifyRoundTripLatency"),

      MISSED_ROUNDTRIP("missedRoundtrip");

      @NonNull @Getter final String op;

      OP(@NonNull String op) {
        this.op = op;
      }
    }
  }
}
