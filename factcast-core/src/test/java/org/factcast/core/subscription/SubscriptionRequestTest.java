package org.factcast.core.subscription;

import static org.junit.Assert.*;

import org.factcast.core.spec.FactSpec;
import org.junit.Test;

public class SubscriptionRequestTest {

    @Test(expected = NullPointerException.class)
    public void testCatchupNullSpec() throws Exception {
        SubscriptionRequest.catchup(null);
    }

    @Test(expected = NullPointerException.class)
    public void testFollowNullSpec() throws Exception {
        SubscriptionRequest.follow(null);
    }

    @Test(expected = NullPointerException.class)
    public void testFollowDelayNullSpec() throws Exception {
        SubscriptionRequest.follow(1, null);
    }

    @Test
    public void testCatchup() throws Exception {
        FactSpec s = FactSpec.ns("xx");
        final SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        assertTrue(r.specs().contains(s));
        assertEquals(1, r.specs().size());
    }

    @Test
    public void testFollow() throws Exception {
        FactSpec s = FactSpec.ns("xx");
        final SubscriptionRequest r = SubscriptionRequest.follow(s).fromScratch();
        assertTrue(r.specs().contains(s));
        assertEquals(1, r.specs().size());
    }

    @Test
    public void testFollowMaxDelay() throws Exception {
        FactSpec s = FactSpec.ns("xx");
        final SubscriptionRequest r = SubscriptionRequest.follow(7, s).fromScratch();
        assertTrue(r.specs().contains(s));
        assertEquals(1, r.specs().size());
        assertEquals(7, r.maxBatchDelayInMs());
    }
}
