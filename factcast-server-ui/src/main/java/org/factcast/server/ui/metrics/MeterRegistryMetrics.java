/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.ui.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

@Slf4j
@RequiredArgsConstructor
public class MeterRegistryMetrics implements InitializingBean, UiMetrics {
  private final MeterRegistry meterRegistry;

  static final String DURATION_METRIC_NAME = "factcast.ui.timer";
  static final String TAG_OPERATION_KEY = "operation";
  static final String TAG_EXCEPTION_KEY = "exception";
  static final String TAG_EXCEPTION_VALUE_NONE = "None";

  @Override
  public void timePluginExecution(@NonNull String pluginDisplayName, @NonNull Runnable r) {
    time(Operations.PLUGIN_EXECUTION, Tags.of("displayName", pluginDisplayName), r);
  }

  @Override
  public <T> T timeFactProcessing(@NonNull Supplier<T> r) {
    return time(Operations.FACT_PROCESSING, Tags.empty(), r);
  }

  public void time(@NonNull Operations operation, @NonNull Tags tags, @NonNull Runnable r) {
    Timer.Sample sample = Timer.start();
    Exception exception = null;
    try {
      r.run();
    } catch (Exception e) {
      exception = e;
      throw e;
    } finally {
      time(operation, sample, tags, exception);
    }
  }

  public <T> T time(@NonNull Operations operation, @NonNull Tags tags, @NonNull Supplier<T> s) {
    Timer.Sample sample = Timer.start();
    Exception exception = null;
    try {
      return s.get();
    } catch (Exception e) {
      exception = e;
      throw e;
    } finally {
      time(operation, sample, tags, exception);
    }
  }

  @NonNull
  private Timer timer(
      @NonNull Operations operation, @NonNull Tags tags, @NonNull String exceptionTagValue) {
    final var fullTags = forOperation(operation, exceptionTagValue).and(tags);
    return Timer.builder(DURATION_METRIC_NAME).tags(fullTags).register(meterRegistry);
  }

  private void time(
      @NonNull Operations operation,
      @NonNull Timer.Sample sample,
      @NonNull Tags tags,
      Exception e) {
    try {
      String exceptionTagValue = mapException(e);
      sample.stop(timer(operation, tags, exceptionTagValue));
    } catch (Exception exception) {
      log.warn("Failed timing operation!", exception);
    }
  }

  @NonNull
  private static String mapException(Exception e) {
    if (e == null) {
      return TAG_EXCEPTION_VALUE_NONE;
    }
    return e.getClass().getSimpleName();
  }

  private Tags forOperation(@NonNull Operations operation, @NonNull String exceptionTagValue) {
    return Tags.of(
        Tag.of(TAG_OPERATION_KEY, operation.opsName()),
        Tag.of(TAG_EXCEPTION_KEY, exceptionTagValue));
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    /*
     * Register all non-exceptional meters, so that an operational dashboard
     * can visualize all possible operations dynamically without hardcoding
     * them.
     */
    for (Operations op : Operations.values()) {
      timer(op, Tags.empty(), TAG_EXCEPTION_VALUE_NONE);
    }
  }
}
