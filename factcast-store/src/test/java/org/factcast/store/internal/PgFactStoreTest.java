package org.factcast.store.internal;

import java.util.*;
import java.util.function.*;

import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.snapcache.PgSnapshotCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import lombok.NonNull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PgFactStoreTest {

  //TODO plenty of opportunities for unit tests

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

  @Nested
  class WhenFetchingByIdAndVersion {
    private final UUID ID = UUID.randomUUID();
    private final int VERSION = 11;

    @SuppressWarnings("rawtypes")
    @BeforeEach
    void setup() {
      when(metrics.time(any(), any(Supplier.class)))
          .thenAnswer(
              i -> {
                Supplier argument = i.getArgument(1);
                return argument.get();
              });
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

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenSubscribing {
    @Mock private @NonNull SubscriptionRequestTO request;
    @Mock private @NonNull FactObserver observer;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenSerialingOf {
    private final UUID FACT_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenEnumeratingNamespaces {
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenEnumeratingTypes {
    private final String NS = "NS";

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenPublishingIfUnchanged {
    @Mock private Fact fact;
    @Mock private @NonNull Optional<StateToken> optionalToken;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenGettingStateFor {
    @Mock private FactSpec factSpec;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenGettingStateForWithSerial {
    private final long LAST_MATCHING_SERIAL = 43;
    @Mock private FactSpec factSpec;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenGettingCurrentStateFor {
    @Mock private FactSpec factSpec;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenCurrentingTime {
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenGettingSnapshot {
    @Mock private @NonNull SnapshotId id;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenClearingSnapshot {
    @Mock private @NonNull SnapshotId id;

    @BeforeEach
    void setup() {}
  }
}
