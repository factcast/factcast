package org.factcast.store.pgsql.internal.catchup.paged;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.PGConfigurationProperties;
import org.factcast.store.pgsql.internal.PGConstants;
import org.factcast.store.pgsql.internal.catchup.PGCatchUpFetchPage;
import org.factcast.store.pgsql.internal.rowmapper.PGIdFactExtractor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class PGCatchUpFetchPage0Test {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private PGConfigurationProperties properties;

    @Mock
    private SubscriptionRequestTO req;

    private PGCatchUpFetchPage uut;

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testFetchIdFacts() throws Exception {
        uut = new PGCatchUpFetchPage(jdbc, properties.getPageSize(), req, 12);
        uut.fetchIdFacts(new AtomicLong());

        verify(jdbc).query(eq(PGConstants.SELECT_ID_FROM_CATCHUP), any(
                PreparedStatementSetter.class), any(PGIdFactExtractor.class));
    }

}
