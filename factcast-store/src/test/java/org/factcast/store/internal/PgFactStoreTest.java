package org.factcast.store.internal;

import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.State;
import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.snapcache.PgSnapshotCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.*;

import lombok.NonNull;
import lombok.SneakyThrows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PgFactStoreTest {

  // TODO plenty of opportunities for unit tests

  @Mock private @NonNull JdbcTemplate jdbcTemplate;
  @Mock private @NonNull PgSubscriptionFactory subscriptionFactory;
  @Mock private @NonNull FactTableWriteLock lock;
  @Mock private @NonNull FactTransformerService factTransformerService;
  @Mock private @NonNull PgFactIdToSerialMapper pgFactIdToSerialMapper;
  @Mock private @NonNull PgMetrics metrics;
  @Mock private @NonNull PgSnapshotCache snapCache;
  @Mock private @NonNull TokenStore tokenStore;
  @InjectMocks private PgFactStore underTest;

  @Nested
  class WhenFetchingById {
    private final UUID ID = UUID.randomUUID();

    @BeforeEach
    void setup() {}
  }

  private void configureMetricTimeSupplier() {
    when(metrics.time(any(), any(Supplier.class)))
        .thenAnswer(
            i -> {
              Supplier argument = i.getArgument(1);
              return argument.get();
            });
  }

  private void configureMetricTimeRunnable() {
    doAnswer(
            i -> {
              Runnable argument = i.getArgument(1);
              argument.run();
              return null;
            })
        .when(metrics)
        .time(any(), any(Runnable.class));
  }

  @Nested
  class WhenFetchingByIdAndVersion {
    private final UUID ID = UUID.randomUUID();
    private final int VERSION = 11;

    @SuppressWarnings("rawtypes")
    @BeforeEach
    void setup() {
      configureMetricTimeSupplier();
    }

    @Test
    void testFetchByIdAndMatchingVersion() {

      UUID id = UUID.randomUUID();
      Fact factAsPublished = Fact.builder().ns("ns").type("type").version(1).buildWithoutPayload();
      when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
          .thenReturn(Lists.newArrayList(factAsPublished));

      assertThat(underTest.fetchByIdAndVersion(id, 1)).isNotEmpty().hasValue(factAsPublished);
    }

    @Test
    void testFetchByIdWith0Version() {

      UUID id = UUID.randomUUID();
      Fact factAsPublished = Fact.builder().ns("ns").type("type").version(1).buildWithoutPayload();
      when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
          .thenReturn(Lists.newArrayList(factAsPublished));

      assertThat(underTest.fetchByIdAndVersion(id, 0)).isNotEmpty().hasValue(factAsPublished);
    }

    @Test
    void testFetchByIdWithUnmatchedVersion() {

      UUID id = UUID.randomUUID();
      Fact factAsPublished = Fact.builder().ns("ns").type("type").version(1).buildWithoutPayload();
      Fact transformedFact = Fact.builder().ns("ns").type("type").version(27).buildWithoutPayload();
      when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
          .thenReturn(Lists.newArrayList(factAsPublished));
      ArgumentCaptor<TransformationRequest> reqCaptor =
          ArgumentCaptor.forClass(TransformationRequest.class);
      when(factTransformerService.transform(reqCaptor.capture())).thenReturn(transformedFact);

      assertThat(underTest.fetchByIdAndVersion(id, 27)).isNotEmpty().hasValue(transformedFact);
      assertThat(reqCaptor.getValue().targetVersions()).hasSize(1).containsExactly(27);
    }
  }

  @Nested
  class WhenPublishing {
    @Mock private Fact fact;

    @SuppressWarnings("rawtypes")
    @BeforeEach
    void setup() {
      configureMetricTimeRunnable();
    }

    @Test
    void publishLock() {
      underTest.publish(Collections.singletonList(fact));
      verify(jdbcTemplate)
          .batchUpdate(
              eq(PgConstants.INSERT_FACT),
              eq(Lists.newArrayList(fact)),
              eq(Integer.MAX_VALUE),
              any(ParameterizedPreparedStatementSetter.class));
      verify(lock).aquireExclusiveTXLock();
    }
  }

  @Nested
  class WhenSubscribing {
    @Mock private @NonNull SubscriptionRequestTO request;
    @Mock private @NonNull FactObserver observer;
    @Mock private @NonNull Subscription sub;

    @BeforeEach
    void setup() {
      configureMetricTimeSupplier();
    }

    @Test
    void name() {
      when(subscriptionFactory.subscribe(request, observer)).thenReturn(sub);
      Subscription s = underTest.subscribe(request, observer);
      assertThat(s).isSameAs(sub);
    }
  }

  @Nested
  class WhenSerialingOf {
    private final UUID FACT_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {}

    @Test
    void delegates() {
      UUID factId = UUID.randomUUID();
      when(pgFactIdToSerialMapper.retrieve(factId)).thenReturn(12L);
      assertThat(underTest.serialOf(factId)).hasValue(12L);
    }

    @Test
    void delegatesNoSerialFound() {
      UUID factId = UUID.randomUUID();
      when(pgFactIdToSerialMapper.retrieve(factId)).thenReturn(0L);
      assertThat(underTest.serialOf(factId)).isEmpty();
    }
  }

  @Nested
  class WhenEnumeratingNamespaces {
    @SuppressWarnings("rawtypes")
    @BeforeEach
    void setup() {
      configureMetricTimeSupplier();
    }

    @Test
    void name() {
      underTest.enumerateNamespaces();
      verify(jdbcTemplate).query(eq(PgConstants.SELECT_DISTINCT_NAMESPACE), any(RowMapper.class));
    }
  }

  @Nested
  class WhenEnumeratingTypes {
    private final String NS = "NS";

    @BeforeEach
    void setup() {
      configureMetricTimeSupplier();
    }

    @Test
    void name() {
      underTest.enumerateTypes("ns1");
      verify(jdbcTemplate)
          .query(
              eq(PgConstants.SELECT_DISTINCT_TYPE_IN_NAMESPACE),
              eq(new Object[] {"ns1"}),
              any(RowMapper.class));
    }
  }

  @Nested
  class WhenPublishingIfUnchanged {
    @Mock private Fact fact;
    @Mock private @NonNull StateToken optionalToken;
    @Mock private State state;

    @BeforeEach
    void setup() {
      configureMetricTimeSupplier();
    }

    @Test
    void noToken() {

      underTest = spy(underTest);

      boolean b = underTest.publishIfUnchanged(Lists.newArrayList(fact), Optional.empty());
      verify(lock).aquireExclusiveTXLock();
      assertThat(b).isTrue();
      verify(underTest).publish(any(List.class));
    }

    @Test
    void brokenToken() {

      underTest = spy(underTest);

      List<FactSpec> specs = Lists.newArrayList(FactSpec.ns("hubba"));
      when(state.specs()).thenReturn(specs);
      when(state.serialOfLastMatchingFact()).thenReturn(10L);
      when(tokenStore.get(optionalToken)).thenReturn(Optional.of(state));
      when(jdbcTemplate.query(
              anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class)))
          .thenReturn(32L);

      boolean b =
          underTest.publishIfUnchanged(Lists.newArrayList(fact), Optional.of(optionalToken));
      verify(lock).aquireExclusiveTXLock();
      assertThat(b).isFalse();
      verify(underTest, never()).publish(any(List.class));
    }

    @Test
    void currentToken() {

      underTest = spy(underTest);

      List<FactSpec> specs = Lists.newArrayList(FactSpec.ns("hubba"));
      when(state.specs()).thenReturn(specs);
      when(state.serialOfLastMatchingFact()).thenReturn(32L);
      when(tokenStore.get(optionalToken)).thenReturn(Optional.of(state));
      // query for newer serial should return 0
      when(jdbcTemplate.query(
              anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class)))
          .thenReturn(0L);

      boolean b =
          underTest.publishIfUnchanged(Lists.newArrayList(fact), Optional.of(optionalToken));
      verify(lock).aquireExclusiveTXLock();
      assertThat(b).isTrue();
      verify(underTest).publish(any(List.class));
    }
  }

  @Nested
  class WhenGettingStateFor {
    @Mock private FactSpec factSpec;

    @BeforeEach
    void setup() {
      configureMetricTimeSupplier();
    }

    @SneakyThrows
    @Test
    void name() {
      FactSpec spec1 = FactSpec.ns("ns1").type("type1");
      List<FactSpec> specs = Lists.newArrayList(spec1);

      PgQueryBuilder pgQueryBuilder = new PgQueryBuilder(specs);
      String stateSQL = pgQueryBuilder.createStateSQL();
      PreparedStatementSetter statementSetter =
          pgQueryBuilder.createStatementSetter(new AtomicLong(0));

      ArgumentCaptor<PreparedStatementSetter> captor =
          ArgumentCaptor.forClass(PreparedStatementSetter.class);
      when(jdbcTemplate.query(eq(stateSQL), captor.capture(), any(ResultSetExtractor.class)))
          .thenReturn(32L);
      assertThat(underTest.getStateFor(specs, 16L).serialOfLastMatchingFact()).isEqualTo(32L);

      PreparedStatement ps = mock(PreparedStatement.class);
      captor.getValue().setValues(ps);
      verify(ps).setLong(3, 16L);
    }
  }

  @Nested
  class WhenGettingStateForWithSerial {
    private final long LAST_MATCHING_SERIAL = 43;
    @Mock private FactSpec factSpec;

    @BeforeEach
    void setup() {
      configureMetricTimeSupplier();
    }

    @SneakyThrows
    @Test
    void name() {
      FactSpec spec1 = FactSpec.ns("ns1").type("type1");
      List<FactSpec> specs = Lists.newArrayList(spec1);

      PgQueryBuilder pgQueryBuilder = new PgQueryBuilder(specs);
      String stateSQL = pgQueryBuilder.createStateSQL();
      PreparedStatementSetter statementSetter =
          pgQueryBuilder.createStatementSetter(new AtomicLong(12));
      ArgumentCaptor<PreparedStatementSetter> captor =
          ArgumentCaptor.forClass(PreparedStatementSetter.class);
      when(jdbcTemplate.query(eq(stateSQL), captor.capture(), any(ResultSetExtractor.class)))
          .thenReturn(32L);
      assertThat(underTest.getStateFor(specs, 16L).serialOfLastMatchingFact()).isEqualTo(32L);

      PreparedStatement ps = mock(PreparedStatement.class);
      captor.getValue().setValues(ps);
      verify(ps).setLong(3, 16L);
    }
  }

  @Nested
  class WhenGettingCurrentStateFor {
    @Mock private FactSpec factSpec;

    @BeforeEach
    void setup() {
      configureMetricTimeSupplier();
    }

    @SneakyThrows
    @Test
    void name() {
      FactSpec spec1 = FactSpec.ns("ns1").type("type1");
      List<FactSpec> specs = Lists.newArrayList(spec1);

      PgQueryBuilder pgQueryBuilder = new PgQueryBuilder(specs);
      String stateSQL = pgQueryBuilder.createStateSQL();
      when(jdbcTemplate.queryForObject(PgConstants.LAST_SERIAL_IN_LOG, Long.class)).thenReturn(32L);
      assertThat(underTest.getCurrentStateFor(specs).serialOfLastMatchingFact()).isEqualTo(32L);
    }
  }

  @Nested
  class WhenCurrentingTime {

    @SneakyThrows
    @Test
    void name() {
      when(jdbcTemplate.queryForObject(PgConstants.CURRENT_TIME_MILLIS, Long.class))
          .thenReturn(123L);
      assertThat(underTest.currentTime()).isEqualTo(123L);
    }
  }

  @Nested
  class WhenGettingSnapshot {
    @Mock private @NonNull SnapshotId id;
    @Mock private Snapshot snap;

    @BeforeEach
    void setup() {
      configureMetricTimeSupplier();
    }

    @SneakyThrows
    @Test
    void unknown() {
      when(snapCache.getSnapshot(id)).thenReturn(Optional.empty());
      assertThat(underTest.getSnapshot(id)).isEmpty();
    }

    @SneakyThrows
    @Test
    void known() {
      when(snapCache.getSnapshot(id)).thenReturn(Optional.of(snap));
      assertThat(underTest.getSnapshot(id)).isNotEmpty().hasValue(snap);
    }
  }

  @Nested
  class WhenSettingSnapshot {
    @Mock private Snapshot snap;

    @BeforeEach
    void setup() {
      configureMetricTimeRunnable();
    }

    @SneakyThrows
    @Test
    void name() {
      underTest.setSnapshot(snap);
      verify(snapCache).setSnapshot(snap);
    }
  }

  @Nested
  class WhenClearingSnapshot {
    @Mock private @NonNull SnapshotId id;

    @BeforeEach
    void setup() {
      configureMetricTimeRunnable();
    }

    @SneakyThrows
    @Test
    void clear() {
      underTest.clearSnapshot(id);
      verify(snapCache).clearSnapshot(id);
      ;
    }
  }
}
