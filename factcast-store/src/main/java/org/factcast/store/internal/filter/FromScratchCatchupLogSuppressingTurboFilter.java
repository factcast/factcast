/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.NonNull;
import org.slf4j.MDC;
import org.slf4j.Marker;

/**
 * Suppresses log events below a configured level on threads that are performing a "from scratch"
 * catchup, but only after a configurable number of log events have been allowed through (the
 * threshold). This allows initial debugging context to be logged while protecting downstream log
 * aggregators (e.g. Grafana) from being overwhelmed during large catchups.
 *
 * <p>Once the threshold is exceeded, an optional sampling mode can let 1 out of every {@code
 * sampleRate} suppressed events through, providing a steady trickle of diagnostic logs throughout
 * the entire catchup instead of complete silence.
 *
 * <p>The catchup thread calls {@link #beginCatchup(String)} before starting and {@link
 * #endCatchup()} when done. These methods manage the MDC attribute and a per-catchup event counter.
 * The counter is shared across all threads that inherit the same MDC (e.g. ForkJoinPool workers
 * that propagate MDC from the catchup thread).
 */
public class FromScratchCatchupLogSuppressingTurboFilter extends TurboFilter {

  public static final String MDC_KEY_FROM_SCRATCH = "factcast.catchup.fromscratch";

  private static final ConcurrentHashMap<String, AtomicInteger> counters =
      new ConcurrentHashMap<>();

  private final Level minLevel;
  private final int threshold;
  private final int sampleRate;

  public FromScratchCatchupLogSuppressingTurboFilter(
      @NonNull Level minLevel, int threshold, int sampleRate) {
    this.minLevel = minLevel;
    this.threshold = threshold;
    this.sampleRate = sampleRate;
  }

  /**
   * Marks the current thread as performing a from-scratch catchup and registers a shared event
   * counter.
   *
   * @param catchupId a unique identifier for this catchup (e.g. from {@code
   *     SubscriptionRequestTO.debugInfo()})
   */
  public static void beginCatchup(String catchupId) {
    if (catchupId == null) {
      catchupId = UUID.randomUUID().toString();
    }
    counters.put(catchupId, new AtomicInteger(0));
    MDC.put(MDC_KEY_FROM_SCRATCH, catchupId);
  }

  /**
   * Removes the from-scratch catchup marker and deregisters the event counter for the current
   * thread. Safe to call even if {@link #beginCatchup(String)} was never called.
   */
  public static void endCatchup() {
    String catchupId = MDC.get(MDC_KEY_FROM_SCRATCH);
    if (catchupId != null) {
      counters.remove(catchupId);
      MDC.remove(MDC_KEY_FROM_SCRATCH);
    }
  }

  @Override
  public FilterReply decide(
      Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
    String catchupId = MDC.get(MDC_KEY_FROM_SCRATCH);
    if (catchupId == null) {
      return FilterReply.NEUTRAL;
    }
    if (level.toInt() < minLevel.toInt()) {
      AtomicInteger counter = counters.get(catchupId);
      if (counter == null) {
        return FilterReply.DENY;
      }
      int count = counter.incrementAndGet();
      if (count > threshold) {
        if (sampleRate > 0 && count % sampleRate == 0) {
          return FilterReply.NEUTRAL;
        }
        return FilterReply.DENY;
      }
    }
    return FilterReply.NEUTRAL;
  }
}
