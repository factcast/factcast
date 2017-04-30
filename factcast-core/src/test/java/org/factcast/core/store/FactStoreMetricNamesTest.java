package org.factcast.core.store;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class FactStoreMetricNamesTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testFactStoreMetricNames() throws Exception {
        final String type = "something";
        FactStoreMetricNames n = new FactStoreMetricNames(type);

        assertTrue(n.factPublished().startsWith("factstore." + type + "."));
        assertTrue(n.factPublishingFailed().startsWith("factstore." + type + "."));
        assertTrue(n.factPublishingLatency().startsWith("factstore." + type + "."));
        assertEquals(type, n.type());

    }

    @Test(expected = NullPointerException.class)
    public void testFactStoreMetricNamesNull() throws Exception {
        new FactStoreMetricNames(null);
    }

}
