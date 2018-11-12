package org.factcast.store.pgsql;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PGConfigurationProperties0Test {

    private PGConfigurationProperties uut = new PGConfigurationProperties();

    @Test
    public void testGetPageSizeForIds() {
        assertEquals(100000, uut.getPageSizeForIds());
    }

    @Test
    public void testGetQueueSizeForIds() {
        assertEquals(100000, uut.getQueueSizeForIds());
    }

    @Test
    public void testGetFetchSizeForIds() {
        assertEquals(25000, uut.getFetchSizeForIds());
    }

    @Test
    public void testGetFetchSize() {
        assertEquals(250, uut.getFetchSize());
    }

}
