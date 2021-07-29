package org.factcast.store.pgsql.internal;

import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import lombok.val;
import org.factcast.core.subscription.FactTransformersFactory;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.store.pgsql.internal.catchup.PgCatchupFactory;
import org.factcast.store.pgsql.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.pgsql.internal.query.PgLatestSerialFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PgSubscriptionFactoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private EventBus eventBus;
  @Mock private PgFactIdToSerialMapper idToSerialMapper;
  @Mock private PgLatestSerialFetcher fetcher;
  @Mock private PgCatchupFactory catchupFactory;
  @Mock private FactTransformersFactory transformersFactory;
  @Mock private FastForwardTarget target;
  @Mock private PgMetrics metrics;
  @InjectMocks private PgSubscriptionFactory underTest;

  @Nested
  class WhenSubscribing {
    @Mock private SubscriptionRequestTO req;
    @Mock private FactObserver observer;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenConnecting {
    @Mock private SubscriptionRequestTO req;
    @Mock private SubscriptionImpl subscription;
    @Mock private PgFactStream pgsub;

    @Test
    void testConnect_happyCase() {
      underTest.connect(req, subscription, pgsub).run();

      verify(pgsub).connect(req);
    }

    @Test
    void testConnect_transformationException() {
      val e = new TransformationException("foo");

      doThrow(e).when(pgsub).connect(req);

      underTest.connect(req, subscription, pgsub).run();

      verify(pgsub).connect(req);
      verify(subscription).notifyError(e);
    }

    @Test
    void testConnect_someException() {
      val e = new IllegalArgumentException("foo");

      doThrow(e).when(pgsub).connect(req);

      underTest.connect(req, subscription, pgsub).run();

      verify(pgsub).connect(req);
      verify(subscription).notifyError(e);
    }
  }
}
