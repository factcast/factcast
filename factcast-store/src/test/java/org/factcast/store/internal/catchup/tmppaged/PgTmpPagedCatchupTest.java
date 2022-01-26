package org.factcast.store.internal.catchup.tmppaged;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.jdbc.PgConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementSetter;

@ExtendWith(MockitoExtension.class)
class PgTmpPagedCatchupTest {

  @Mock
  @NonNull PgConnectionSupplier connectionSupplier;
  @Mock
  @NonNull StoreConfigurationProperties props;
  @Mock
  @NonNull SubscriptionRequestTO request;
  @Mock
  @NonNull PgPostQueryMatcher postQueryMatcher;
  @Mock
  @NonNull SubscriptionImpl subscription;
  @Mock
  @NonNull AtomicLong serial;
  @Mock
  @NonNull PgMetrics metrics;
  @Mock
  @NonNull Counter counter;
  @InjectMocks
  PgTmpPagedCatchup underTest;

  @Nested
  class WhenRunning {
    @BeforeEach
    void setup() {
    }

    @SneakyThrows
    @Test
    void connectionHandling() {
      PgConnection con = mock(PgConnection.class);
      when(connectionSupplier.get()).thenReturn(con);

      final var uut = spy(underTest);
      doNothing().when(uut).fetch(any());

      uut.run();

      verify(con).close();
    }
  }

  @Nested
  class WhenFetching {
    @Mock(lenient = true)
    @NonNull JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
      doNothing().when(jdbc).execute(anyString());

      when(postQueryMatcher.canBeSkipped()).thenReturn(true);
    }

    @Test
    void createsTempTable() {
      when(jdbc.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(0L);
      underTest.fetch(jdbc);
      verify(jdbc).execute("CREATE TEMPORARY TABLE catchup(ser bigint)");
    }

    @Test
    void createsIndex() {
      when(jdbc.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(0L);
      underTest.fetch(jdbc);
      verify(jdbc).execute("CREATE INDEX catchup_tmp_idx1 ON catchup(ser ASC)");
    }

    @Test
    void notifies() {
      when(jdbc.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(1L);
      List<Fact> testFactList = new ArrayList<Fact>();
      Fact testFact = Fact.builder().buildWithoutPayload();
      testFactList.add(testFact);
      when(jdbc
              .query(
                      eq(PgConstants.SELECT_FACT_FROM_CATCHUP),
                      any(PreparedStatementSetter.class),
                      any(PgFactExtractor.class)))
              .thenReturn(testFactList)
              .thenReturn(new ArrayList<Fact>());
      // stop iteration after first fetch
      when(metrics.counter(StoreMetrics.EVENT.CATCHUP_FACT)).thenReturn(mock(Counter.class));
      underTest.fetch(jdbc);
      verify(subscription).notifyElement(testFact);
    }

    @Test
    void counts() {
      when(metrics.counter(any())).thenReturn(counter);
      when(jdbc.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(1L);
      List<Fact> testFactList = Lists.newArrayList(new TestFact(), new TestFact());
      when(jdbc
              .query(
                      eq(PgConstants.SELECT_FACT_FROM_CATCHUP),
                      any(PreparedStatementSetter.class),
                      any(PgFactExtractor.class)))
              .thenReturn(testFactList)
              .thenReturn(Collections.emptyList());

      underTest.fetch(jdbc);

      verify(counter).increment(testFactList.size());
    }
  }
}
