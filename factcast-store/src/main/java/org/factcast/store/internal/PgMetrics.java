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
package org.factcast.store.internal;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

@Slf4j
public class PgMetrics implements InitializingBean {

  @NonNull private final MeterRegistry registry;

  public PgMetrics(@NonNull MeterRegistry registry) {
    this.registry = registry;
  }

  @NonNull
  public Counter counter(@NonNull StoreMetrics.EVENT operation) {
    Tags tags = forOperation(operation, StoreMetrics.TAG_EXCEPTION_VALUE_NONE);
    // omitting the meter description here
    return Counter.builder(StoreMetrics.METER_METRIC_NAME).tags(tags).register(registry);
  }

  @NonNull
  public DistributionSummary distributionSummary(@NonNull StoreMetrics.VALUE operation) {
    Tags tags = forOperation(operation, StoreMetrics.TAG_EXCEPTION_VALUE_NONE);
    return DistributionSummary.builder(StoreMetrics.METER_METRIC_NAME)
        .tags(tags)
        .register(registry);
  }

  private Tags forOperation(@NonNull MetricName operation, @NonNull String exceptionTagValue) {
    return Tags.of(
        Tag.of(StoreMetrics.TAG_STORE_KEY, StoreMetrics.TAG_STORE_VALUE),
        Tag.of(StoreMetrics.TAG_OPERATION_KEY, operation.getName()),
        Tag.of(StoreMetrics.TAG_EXCEPTION_KEY, exceptionTagValue));
  }

  public void time(@NonNull StoreMetrics.OP operation, @NonNull Runnable r) {
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

  public <T> T time(@NonNull StoreMetrics.OP operation, @NonNull Supplier<T> s) {
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

  private void time(@NonNull StoreMetrics.OP operation, @NonNull Sample sample, Exception e) {
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
  private Timer timer(@NonNull StoreMetrics.OP operation, @NonNull String exceptionTagValue) {
    Tags tags = forOperation(operation, exceptionTagValue);
    return Timer.builder(StoreMetrics.DURATION_METRIC_NAME).tags(tags).register(registry);
  }

  @NonNull
  public Timer timer(@NonNull StoreMetrics.OP operation) {
    return timer(operation, StoreMetrics.TAG_EXCEPTION_VALUE_NONE);
  }

  public ExecutorService monitor(ExecutorService executor, String name) {
    return ExecutorServiceMetrics.monitor(registry, executor, name);
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    /*
     * Register all non-exceptional meters, so that an operational dashboard
     * can visualize all possible operations dynamically without hardcoding
     * them.
     */
    for (StoreMetrics.OP op : StoreMetrics.OP.values()) {
      timer(op, StoreMetrics.TAG_EXCEPTION_VALUE_NONE);
    }
    for (StoreMetrics.EVENT e : StoreMetrics.EVENT.values()) {
      counter(e);
    }
    for (StoreMetrics.VALUE e : StoreMetrics.VALUE.values()) {
      distributionSummary(e);
    }
  }
}
