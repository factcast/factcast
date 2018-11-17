package org.factcast.store.pgsql.internal.catchup.queue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.PGConfigurationProperties;
import org.factcast.store.pgsql.internal.PGPostQueryMatcher;
import org.factcast.store.pgsql.internal.catchup.PGCatchUpFetchPage;
import org.factcast.store.pgsql.internal.query.PGFactIdToSerialMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
public class PGQueueCatchup0Test {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private PGPostQueryMatcher postQueryMatcher;

    @Mock
    private PGConfigurationProperties props;

    @Mock
    private SubscriptionRequestTO request;

    @Mock
    private AtomicLong serial;

    @Mock
    private PGFactIdToSerialMapper serMapper;

    @Mock
    private SubscriptionImpl<Fact> subscription;

    @InjectMocks
    private PGQueueCatchup uut;

    @Test
    public void testDoFetchFacts() {
        final PGCatchUpFetchPage page = mock(PGCatchUpFetchPage.class);
        uut.doFetch(page);
        verify(page).fetchFacts(any(AtomicLong.class));
    }

    @Test
    public void testDoFetchIds() {
        final PGCatchUpFetchPage page = mock(PGCatchUpFetchPage.class);
        when(request.idOnly()).thenReturn(true);
        when(postQueryMatcher.canBeSkipped()).thenReturn(true);
        uut.doFetch(page);
        verify(page).fetchIdFacts(any(AtomicLong.class));
    }
}
