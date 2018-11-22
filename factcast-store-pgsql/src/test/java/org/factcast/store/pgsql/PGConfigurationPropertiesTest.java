package org.factcast.store.pgsql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class PGConfigurationPropertiesTest {

    private PGConfigurationProperties uut = new PGConfigurationProperties();

    @Test
    void testGetPageSizeForIds() {
        assertEquals(100000, uut.getPageSizeForIds());
    }

    @Test
    void testGetQueueSizeForIds() {
        assertEquals(100000, uut.getQueueSizeForIds());
    }

    @Test
    void testGetFetchSizeForIds() {
        assertEquals(25000, uut.getFetchSizeForIds());
    }

    @Test
    void testGetFetchSize() {
        assertEquals(250, uut.getFetchSize());
    }
}
