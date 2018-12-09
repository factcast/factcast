package org.factcast.store.pgsql.internal;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class PGPostQueryMatcherTest {

    @Test
    public void testPGPostQueryMatcher() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new PGPostQueryMatcher(null);
        });
    }

}
