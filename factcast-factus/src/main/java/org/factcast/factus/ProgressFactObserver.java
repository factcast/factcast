package org.factcast.factus;

import static org.factcast.factus.metrics.TagKeys.*;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.metrics.TimedOperation;
import org.factcast.factus.projection.ProgressAware;

@RequiredArgsConstructor
@Slf4j
abstract class ProgressFactObserver implements FactObserver {

  private final ProgressAware target;
  private final long interval;
  private final FactusMetrics metrics;

  @Getter(AccessLevel.PACKAGE)
  @VisibleForTesting
  private FactStreamInfo info;

  private long lastProgress = System.currentTimeMillis();
  private boolean caughtUp = false;

  @Override
  public final void onFactStreamInfo(@NonNull FactStreamInfo info) {
    log.trace("received info {}", info);
    this.info = info;
  }

  @Override
  public final void onNext(@NonNull Fact element) {
    onNextFact(element);

    if (caughtUp) {
      reportCatchupTime(element);
    }

    // not yet caught up
    if (info != null && System.currentTimeMillis() - lastProgress > interval) {
      lastProgress = System.currentTimeMillis();
      target.catchupPercentage(info.calculatePercentage(element.serial()));
    }
  }

  @Override
  public final void onCatchup() {
    caughtUp = true;
    disableProgressTracking();
    // disable progress tracking
    onCatchupSignal();
  }

  @VisibleForTesting
  void disableProgressTracking() {
    info = null;
  }

  @VisibleForTesting
  void reportCatchupTime(@NonNull Fact element) {
    String ts = element.meta("_ts");
    // _ts might not be there in unit testing for instance.
    if (ts != null) {
      long latency = Instant.now().toEpochMilli() - Long.parseLong(ts);
      metrics.timed(
          TimedOperation.EVENT_PROCESSING_LATENCY,
          Tags.of(Tag.of(CLASS, target.getClass().getName())),
          latency);
    }
  }

  protected abstract void onCatchupSignal();

  protected abstract void onNextFact(Fact element);
}
