package org.factcast.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FactStoreMetricNames0Test {

    @Test
    public void testFactStoreMetricNames() {
        final String type = "something";
        FactStoreMetricNames n = new FactStoreMetricNames(type);
        final String typedPrefix = "factstore." + type + ".";
        assertTrue(n.factPublishingMeter().startsWith(typedPrefix));
        assertTrue(n.factPublishingFailed().startsWith(typedPrefix));
        assertTrue(n.factPublishingLatency().startsWith(typedPrefix));
        assertTrue(n.fetchLatency().startsWith(typedPrefix));
        assertTrue(n.connectionFailure().startsWith(typedPrefix));
        assertTrue(n.subscribeCatchup().startsWith(typedPrefix));
        assertTrue(n.subscribeFollow().startsWith(typedPrefix));
        assertEquals(type, n.type());
    }

    @Test
    public void testFactStoreMetricNamesNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new FactStoreMetricNames(null);
        });
    }
}
