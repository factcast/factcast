package org.factcast.core.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SubscriptionRequest0Test {

    @Test
    public void testCatchupNullSpec() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            SubscriptionRequest.catchup((FactSpec) null);
        });
    }

    @Test
    public void testFollowNullSpec() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            SubscriptionRequest.follow((FactSpec) null);
        });
    }

    @Test
    public void testFollowDelayNullSpec() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            SubscriptionRequest.follow(1, null);
        });
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
