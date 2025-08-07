/*
 * Copyright © 2017-2022 factcast.org
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.State;
import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.concurrency.*;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.registry.SchemaRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.PlatformTransactionManager;

@SuppressWarnings("rawtypes")
@ExtendWith(MockitoExtension.class)
class PgFactStoreTest {

  @Mock private @NonNull JdbcTemplate jdbcTemplate;
  @Mock private @NonNull PgSubscriptionFactory subscriptionFactory;
  @Mock private @NonNull FactTableWriteLock lock;
  @Mock private @NonNull FactTransformerService factTransformerService;
  @Mock private @NonNull PgFactIdToSerialMapper pgFactIdToSerialMapper;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private @NonNull PgMetrics metrics;

  @Mock private @NonNull TokenStore tokenStore;
  @Spy private StoreConfigurationProperties storeConfigurationProperties;
  @Mock SchemaRegistry schemaRegistry;

  @Mock private PlatformTransactionManager platformTransactionManager;
  @Mock private @NonNull ConcurrencyStrategy concurrency;

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

    @BeforeEach
    void setup() {
      configureMetricTimeSupplier();
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

    @BeforeEach
    void setup() {
      configureMetricTimeRunnable();
    }

    @Test
    void publishDelegates() {
      underTest.publish(Collections.singletonList(fact));
      verify(concurrency).publish(eq(Lists.newArrayList(fact)));
    }

    @Test
    void throwOnPublishInReadOnlyMode() {
      when(storeConfigurationProperties.isReadOnlyModeEnabled()).thenReturn(true);

      assertThatThrownBy(() -> underTest.publish(Collections.singletonList(fact)))
          .isInstanceOf(UnsupportedOperationException.class);

      verifyNoInteractions(jdbcTemplate);
      verifyNoInteractions(lock);
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
    void setup() {}

    @Test
    void withoutSchemaRegistry() {
      configureMetricTimeSupplier();
      when(schemaRegistry.isActive()).thenReturn(false);
      underTest.enumerateNamespaces();
      verify(jdbcTemplate).query(eq(PgConstants.SELECT_DISTINCT_NAMESPACE), any(RowMapper.class));
    }

    @Test
    void withSchemaRegistry() {
      underTest = spy(underTest);
      Set<String> ns = Set.of("a", "b");
      when(schemaRegistry.isActive()).thenReturn(true);
      when(schemaRegistry.enumerateNamespaces()).thenReturn(ns);
      assertThat(underTest.enumerateNamespaces()).isSameAs(ns);
      verify(underTest, never()).enumerateNamespacesFromPg();
    }

    @Test
    void usesDirectMode() {
      configureMetricTimeSupplier();
      when(schemaRegistry.isActive()).thenReturn(true);
      when(storeConfigurationProperties.isEnumerationDirectModeEnabled()).thenReturn(true);
      underTest.enumerateNamespaces();
      verify(jdbcTemplate).query(eq(PgConstants.SELECT_DISTINCT_NAMESPACE), any(RowMapper.class));
    }
  }

  @SuppressWarnings("unchecked")
  @Nested
  class WhenEnumeratingTypes {
    private final String NS = "NS";

    @BeforeEach
    void setup() {}

    @Test
    void withoutSchemaRegistry() {
      when(schemaRegistry.isActive()).thenReturn(false);
      configureMetricTimeSupplier();

      underTest.enumerateTypes(NS);
      verify(jdbcTemplate)
          .query(eq(PgConstants.SELECT_DISTINCT_TYPE_IN_NAMESPACE), any(RowMapper.class), same(NS));
    }

    @Test
    void withSchemaRegistry() {

      Set<String> types = Set.of("a", "b");
      underTest = spy(underTest);
      when(schemaRegistry.isActive()).thenReturn(true);
      when(schemaRegistry.enumerateTypes(anyString())).thenReturn(types);

      assertThat(underTest.enumerateTypes("foo")).isSameAs(types);

      verify(underTest, never()).enumerateTypesFromPg(any());
    }

    @Test
    void usesDirectMode() {
      configureMetricTimeSupplier();
      when(schemaRegistry.isActive()).thenReturn(true);
      when(storeConfigurationProperties.isEnumerationDirectModeEnabled()).thenReturn(true);
      underTest.enumerateTypes(NS);
      verify(jdbcTemplate)
          .query(eq(PgConstants.SELECT_DISTINCT_TYPE_IN_NAMESPACE), any(RowMapper.class), same(NS));
    }
  }

  @SuppressWarnings("unchecked")
  @Nested
  class WhenEnumeratingVersions {
    private final String NS = "NS";
    private final String TYPE = "TYPE";

    @BeforeEach
    void setup() {}

    @Test
    void withoutSchemaRegistry() {
      when(schemaRegistry.isActive()).thenReturn(false);
      configureMetricTimeSupplier();

      underTest.enumerateVersions(NS, TYPE);
      verify(jdbcTemplate)
          .query(
              eq(PgConstants.SELECT_DISTINCT_VERSIONS_FOR_NS_AND_TYPE),
              any(RowMapper.class),
              same(NS),
              same(TYPE));
    }

    @Test
    void withSchemaRegistry() {

      Set<Integer> versions = Set.of(1, 2);
      underTest = spy(underTest);
      when(schemaRegistry.isActive()).thenReturn(true);
      when(schemaRegistry.enumerateVersions(anyString(), anyString())).thenReturn(versions);

      assertThat(underTest.enumerateVersions(NS, TYPE)).isSameAs(versions);

      verify(underTest, never()).enumerateVersionsFromPg(NS, TYPE);
    }

    @Test
    void usesDirectMode() {
      configureMetricTimeSupplier();
      when(schemaRegistry.isActive()).thenReturn(true);
      when(storeConfigurationProperties.isEnumerationDirectModeEnabled()).thenReturn(true);
      underTest.enumerateVersions(NS, TYPE);
      verify(jdbcTemplate)
          .query(
              eq(PgConstants.SELECT_DISTINCT_VERSIONS_FOR_NS_AND_TYPE),
              any(RowMapper.class),
              same(NS),
              same(TYPE));
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
      assertThat(underTest.hasNoConflictingChangeUntil(1L, Optional.empty())).isTrue();
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

      assertThat(underTest.hasNoConflictingChangeUntil(Long.MAX_VALUE, Optional.of(optionalToken)))
          .isFalse();
    }

    @Test
    void currentToken() {

      underTest = spy(underTest);

      List<FactSpec> specs = Lists.newArrayList(FactSpec.ns("hubba"));
      when(state.specs()).thenReturn(specs);
      when(state.serialOfLastMatchingFact()).thenReturn(32L);
      Optional<State> state = Optional.of(this.state);
      when(tokenStore.get(optionalToken)).thenReturn(state);
      // query for newer serial should return 0
      when(jdbcTemplate.query(
              anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class)))
          .thenReturn(0L);

      assertThat(underTest.hasNoConflictingChangeUntil(Long.MAX_VALUE, Optional.of(optionalToken)))
          .isTrue();
    }

    @Test
    void throwOnPublishIfUnchangedInReadOnlyMode() {
      when(storeConfigurationProperties.isReadOnlyModeEnabled()).thenReturn(true);

      assertThatThrownBy(
              () ->
                  underTest.publishIfUnchanged(
                      Collections.singletonList(fact), Optional.of(optionalToken)))
          .isInstanceOf(UnsupportedOperationException.class);

      verifyNoInteractions(jdbcTemplate);
      verifyNoInteractions(lock);
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
    // TODO name
    void needsAName() {
      FactSpec spec1 = FactSpec.ns("ns1").type("type1");
      List<FactSpec> specs = Lists.newArrayList(spec1);

      PgQueryBuilder pgQueryBuilder = new PgQueryBuilder(specs);
      String stateSQL = pgQueryBuilder.createStateSQL(null);
      PreparedStatementSetter statementSetter =
          pgQueryBuilder.createStatementSetter(new AtomicLong(0), null);

      ArgumentCaptor<PreparedStatementSetter> captor =
          ArgumentCaptor.forClass(PreparedStatementSetter.class);
      when(jdbcTemplate.query(eq(stateSQL), captor.capture(), any(ResultSetExtractor.class)))
          .thenReturn(32L);
      assertThat(underTest.getStateFor(specs, 16L, null).serialOfLastMatchingFact()).isEqualTo(32L);

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
    // TODO name
    void name() {
      FactSpec spec1 = FactSpec.ns("ns1").type("type1");
      List<FactSpec> specs = Lists.newArrayList(spec1);

      PgQueryBuilder pgQueryBuilder = new PgQueryBuilder(specs);
      String stateSQL = pgQueryBuilder.createStateSQL(null);
      PreparedStatementSetter statementSetter =
          pgQueryBuilder.createStatementSetter(new AtomicLong(12), null);
      ArgumentCaptor<PreparedStatementSetter> captor =
          ArgumentCaptor.forClass(PreparedStatementSetter.class);
      when(jdbcTemplate.query(eq(stateSQL), captor.capture(), any(ResultSetExtractor.class)))
          .thenReturn(32L);
      assertThat(underTest.getStateFor(specs, 16L, null).serialOfLastMatchingFact()).isEqualTo(32L);

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
      String stateSQL = pgQueryBuilder.createStateSQL(null);
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
}
