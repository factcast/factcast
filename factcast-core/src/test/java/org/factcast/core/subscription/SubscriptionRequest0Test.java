package org.factcast.core.subscription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.factcast.core.spec.FactSpec;
import org.junit.Test;

public class SubscriptionRequest0Test {

    @Test(expected = NullPointerException.class)
    public void testCatchupNullSpec() {
        SubscriptionRequest.catchup((FactSpec) null);
    }

    @Test(expected = NullPointerException.class)
    public void testFollowNullSpec() {
        SubscriptionRequest.follow((FactSpec) null);
    }

    @Test(expected = NullPointerException.class)
    public void testFollowDelayNullSpec() {
        SubscriptionRequest.follow(1, null);
    }

    @Test
    public void testCatchup() {
        FactSpec s = FactSpec.ns("xx");
        final SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        assertTrue(r.specs().contains(s));
        assertEquals(1, r.specs().size());
    }

    @Test
    public void testFollow() {
        FactSpec s = FactSpec.ns("xx");
        final SubscriptionRequest r = SubscriptionRequest.follow(s).fromScratch();
        assertTrue(r.specs().contains(s));
        assertEquals(1, r.specs().size());
    }

    @Test
    public void testFollowMaxDelay() {
        FactSpec s = FactSpec.ns("xx");
        final SubscriptionRequest r = SubscriptionRequest.follow(7, s).fromScratch();
        assertTrue(r.specs().contains(s));
        assertEquals(1, r.specs().size());
        assertEquals(7, r.maxBatchDelayInMs());
    }
}
