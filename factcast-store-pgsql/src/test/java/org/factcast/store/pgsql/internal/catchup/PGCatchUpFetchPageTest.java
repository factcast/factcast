package org.factcast.store.pgsql.internal.catchup;

import static org.junit.jupiter.api.Assertions.*;

import org.factcast.core.subscription.SubscriptionRequestTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
public class PGCatchUpFetchPageTest {

    @Mock
    SubscriptionRequestTO req;

    @Mock
    JdbcTemplate jdbc;

    @Test
    public void testNullParameterContracts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new PGCatchUpFetchPage(null, 10, req, 1);
        });
        assertThrows(NullPointerException.class, () -> {
            new PGCatchUpFetchPage(jdbc, 10, null, 1);
        });
        PGCatchUpFetchPage uut = new PGCatchUpFetchPage(jdbc, 10, req, 1);
        assertThrows(NullPointerException.class, () -> {
            uut.fetchFacts(null);
        });
        assertThrows(NullPointerException.class, () -> {
            uut.fetchIdFacts(null);
        });
    }

}
