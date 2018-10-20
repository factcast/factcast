package org.factcast.store.pgsql;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PGConfigurationProperties0Test {

    private PGConfigurationProperties uut = new PGConfigurationProperties();

    @Test
    public void testGetPageSizeForIds() throws Exception {
        assertEquals(100000, uut.getPageSizeForIds());
    }

    @Test
    public void testGetQueueSizeForIds() throws Exception {
        assertEquals(100000, uut.getQueueSizeForIds());
    }

    @Test
    public void testGetFetchSizeForIds() throws Exception {
        assertEquals(25000, uut.getFetchSizeForIds());
    }

    @Test
    public void testGetFetchSize() throws Exception {
        assertEquals(250, uut.getFetchSize());
    }

}
