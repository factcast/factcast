package org.factcast.factus;

import static java.lang.Thread.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.Fact;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.metrics.TimedOperation;
import org.factcast.factus.projection.ProgressAware;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractFactObserverTest {
  private static final long INTERVAL = 1;
  private static final long LAST_PROGRESS = 21;
  @Mock private ProgressAware target;
  @Mock private FactusMetrics metrics;
  @Mock private FactStreamInfo info;
  @Mock private Fact lastElement;
  private AbstractFactObserver underTest;

  @BeforeEach
  void setup() {
    underTest =
        Mockito.spy(
            new AbstractFactObserver(target, INTERVAL, metrics) {
              @Override
              protected void onCatchupSignal() {}

              @Override
              protected void onNextFact(Fact element) {}
            });
  }

  @Nested
  class WhenOningFactStreamInfo {
    @Mock private @NonNull FactStreamInfo info;

    @Test
    void remembersInfo() {
      underTest.onFactStreamInfo(info);
      assertThat(underTest.info()).isSameAs(info);
    }
  }

  @Nested
  class WhenOningNext {
    @Mock private @NonNull Fact element;

    @Test
    void delegates() {
      Fact e = Fact.builder().ns("foo").buildWithoutPayload();

      underTest.onNext(e);

      verify(underTest).onNextFact(e);
    }

    @SneakyThrows
    @Test
    void reportsProgress() {
      underTest.onFactStreamInfo(new FactStreamInfo(0, 1000));
      sleep(100); // make sure
      Fact e = Fact.builder().ns("foo").meta("_ser", "1000").buildWithoutPayload();
      underTest.onNext(e);

      verify(target).catchupPercentage(100);
    }
  }

  @Nested
  class WhenOningCatchup {
    @Test
    void disablesProgressTracking() {
      underTest.onCatchup();
      verify(underTest).disableProgressTracking();
    }
  }

  @Nested
  class WhenDisablingProgressTracking {
    @Test
    void nullsInfo() {
      underTest.onFactStreamInfo(new FactStreamInfo(0, 1000));
      underTest.disableProgressTracking();
      assertThat(underTest.info()).isNull();
    }
  }

  @Nested
  class WhenReportingCatchupTime {

    @Test
    void addsMetricAfterCatchup() {
      Fact e =
          Fact.builder()
              .ns("foo")
              .meta("_ts", String.valueOf(System.currentTimeMillis() - 1000))
              .buildWithoutPayload();
      underTest.onCatchup();
      underTest.onNext(e);
      verify(metrics).timed(eq(TimedOperation.EVENT_PROCESSING_LATENCY), any(), anyLong());
    }

    @Test
    void supressesMetricBeforeCatchup() {
      Fact e =
          Fact.builder()
              .ns("foo")
              .meta("_ts", String.valueOf(System.currentTimeMillis() - 1000))
              .buildWithoutPayload();
      underTest.onNext(e);
      verifyNoInteractions(metrics);
    }
  }
}
