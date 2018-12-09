package org.factcast.store.pgsql.internal.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.factcast.core.subscription.SubscriptionRequestTO;
import org.junit.jupiter.api.Test;

public class PGQueryBuilderTest {

    @Test
    public void testPGQueryBuilder() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new PGQueryBuilder(null);
        });

        PGQueryBuilder uut = new PGQueryBuilder(mock(SubscriptionRequestTO.class));
        assertThrows(NullPointerException.class, () -> {
            uut.createStatementSetter(null);
        });
    }

}
