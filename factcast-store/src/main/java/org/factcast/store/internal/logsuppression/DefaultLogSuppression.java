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
package org.factcast.store.internal.logsuppression;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.*;
import org.slf4j.*;

/**
 * Suppresses log events below a configured level on threads that created a Supression until closed
 * but only after a configurable number of log events have been allowed through (the threshold).
 * This allows initial debugging context to be logged while protecting downstream log aggregators
 * (e.g. Grafana) from being overwhelmed during large catchups.
 *
 * <p>Once the threshold is exceeded, an optional sampling mode can let 1 out of every {@code
 * sampleRate} suppressed events through, providing a steady trickle of diagnostic logs throughout
 * the entire catchup instead of complete silence.
 */
@Slf4j
public class DefaultLogSuppression extends AbstractLifecycleBean implements LogSuppression {

  public static final String MDC_KEY = "factcast.store.log-suppression";
  private static final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

  private final Level minLevel;
  private final int threshold;
  private final int sampleRate;
  private Filter filter = null;
  private boolean running;

  public DefaultLogSuppression(@NonNull LogSuppressionProperties suppressionProps) {
    this(
        suppressionProps.getMinLogLevel(),
        suppressionProps.getThreshold(),
        suppressionProps.getSampleRate());
  }

  public DefaultLogSuppression(@NonNull Level minLevel, int threshold, int sampleRate) {
    Preconditions.checkArgument(sampleRate >= 0, "sampleRate must be >= 0");
    Preconditions.checkArgument(threshold >= 0, "threshold must be >= 0");

    this.minLevel = minLevel;
    this.threshold = threshold;
    this.sampleRate = sampleRate;
  }

  @Override
  protected void onStart() {
    filter = new Filter();
    filter.setName("factcast-log-suppression");
    filter.install();
  }

  @Override
  protected void onStop() {
    if (filter != null) {
      filter.uninstall();
    }
    filter = null;
  }

  /**
   * If this is a from scratch catchup and suppression is enabled, the current thread is marked.
   *
   * @param request
   * @return Suppression that must be closed
   */
  @Override
  public Suppression forCatchup(@NonNull SubscriptionRequestTO request) {
    if (request.startingAfter().isEmpty()) {
      return create("catchup-from-scratch-" + request);
    } else return Suppression.NOP;
  }

  private Suppression create(String suppressionId) {
    // This marks the Thread as suppressed
    log.debug("Log suppression '{}' started.", suppressionId);
    counters.put(suppressionId, new AtomicLong(0));
    MDC.put(MDC_KEY, suppressionId);

    return () -> {
      MDC.remove(MDC_KEY);
      counters.remove(suppressionId);
      log.debug("Log suppression '{}' ended.", suppressionId);
    };
  }

  @VisibleForTesting
  Filter filter() {
    if (running) return filter;
    else throw new IllegalStateException("Log suppression not running");
  }

  public class Filter extends TurboFilter {

    @Override
    @SuppressWarnings("java:S1066")
    public FilterReply decide(
        Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
      String suppressionId = MDC.get(MDC_KEY);
      if (suppressionId != null) {
        AtomicLong counter = counters.get(suppressionId);
        if (counter != null) {
          if (!level.isGreaterOrEqual(minLevel)) {

            long count = counter.incrementAndGet();

            if (count > threshold) {
              // check for sampling
              if (sampleRate != 0 && (count - threshold) % sampleRate == 0)
                return FilterReply.NEUTRAL;

              return FilterReply.DENY;
            } else {
              // we're below or equal threshold
              if (count == threshold) {
                addInfo(
                    "Suppression '"
                        + suppressionId
                        + "': Threshold reached, suppressing log lines (with a sample rate of "
                        + sampleRate
                        + ")");
              }
            }
          }
        }
      }
      return FilterReply.NEUTRAL;
    }

    public void install() {
      start();
      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
      setContext(context);
      context.addTurboFilter(this);
    }

    public void uninstall() {
      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
      context.getTurboFilterList().remove(this);
      stop();
    }
  }
}
