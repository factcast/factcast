package org.factcast.core.subscription;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collection;

import org.factcast.core.TestHelper;
import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SubscriptionRequestTest {

    @Test
    void testCatchupNullSpec() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            SubscriptionRequest.catchup((FactSpec) null);
        });
    }

    @Test
    void testFollowNullSpec() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            SubscriptionRequest.follow((FactSpec) null);
        });
    }

    @Test
    void testFollowDelayNullSpec() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            SubscriptionRequest.follow(1, null);
        });
    }

    @Test
    void testCatchup() {
        FactSpec s = FactSpec.ns("xx");
        final SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        assertTrue(r.specs().contains(s));
        assertEquals(1, r.specs().size());
    }

    @Test
    void testCatchupCollection() {
        FactSpec ns1 = FactSpec.ns("ns1");
        FactSpec ns2 = FactSpec.ns("ns2");
        final SubscriptionRequest r = SubscriptionRequest.catchup(Arrays.asList(ns1, ns2))
                .fromScratch();
        assertTrue(r.specs().contains(ns1));
        assertTrue(r.specs().contains(ns2));
        assertEquals(2, r.specs().size());
        assertFalse(r.continuous());
    }

    @Test
    void testFollowCollection() {
        FactSpec ns1 = FactSpec.ns("ns1");
        FactSpec ns2 = FactSpec.ns("ns2");
        final SubscriptionRequest r = SubscriptionRequest.follow(Arrays.asList(ns1, ns2))
                .fromScratch();
        assertTrue(r.specs().contains(ns1));
        assertTrue(r.specs().contains(ns2));
        assertEquals(2, r.specs().size());
        assertTrue(r.continuous());
    }

    @Test
    void testCatchupCollectionNullParameters() {
        TestHelper.expectNPE(() -> {
            SubscriptionRequest.catchup((Collection<FactSpec>) null);
        });
    }

    @Test
    void testFollowCollectionNullParameters() {
        TestHelper.expectNPE(() -> {
            SubscriptionRequest.follow((Collection<FactSpec>) null);
        });
    }

    @Test
    void testFollow() {
        FactSpec s = FactSpec.ns("xx");
        final SubscriptionRequest r = SubscriptionRequest.follow(s).fromScratch();
        assertTrue(r.specs().contains(s));
        assertEquals(1, r.specs().size());
    }

    @Test
    void testFollowMaxDelay() {
        FactSpec s = FactSpec.ns("xx");
        final SubscriptionRequest r = SubscriptionRequest.follow(7, s).fromScratch();
        assertTrue(r.specs().contains(s));
        assertEquals(1, r.specs().size());
        assertEquals(7, r.maxBatchDelayInMs());
    }
}
