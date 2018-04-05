package org.factcast.core.subscription;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.factcast.core.spec.FactSpec;
import org.junit.Test;

public class FluentSubscriptionRequest0Test {

    @Test
    public void testFromSubscription() throws Exception {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromNowOn();

        assertTrue(r.ephemeral());
    }

    @Test(expected = NullPointerException.class)
    public void testFromNull() throws Exception {
        SubscriptionRequest.catchup(FactSpec.ns("foo")).from(null);
    }

    @Test(expected = NullPointerException.class)
    public void testFollowNull() throws Exception {
        SubscriptionRequest.follow((FactSpec) null);
    }

    @Test(expected = NullPointerException.class)
    public void testCatchupNull() throws Exception {
        SubscriptionRequest.catchup((FactSpec) null);
    }

    @Test(expected = NullPointerException.class)
    public void testOrNull() throws Exception {
        SubscriptionRequest.catchup(FactSpec.forMark()).or(null);
    }

    @Test
    public void testToString() throws Exception {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.forMark()).fromScratch();
        assertSame(r.debugInfo(), r.toString());
    }

    @Test
    public void testDebugInfo() throws Exception {
        String debugInfo = SubscriptionRequest.catchup(FactSpec.forMark())
                .fromScratch()
                .debugInfo();
        assertNotNull(debugInfo);
        assertTrue(debugInfo.contains(this.getClass().getSimpleName()));
        assertTrue(debugInfo.contains("testDebugInfo")); // method name
    }

}
