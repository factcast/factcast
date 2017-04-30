package org.factcast.core.subscription;

import static org.junit.Assert.*;

import org.factcast.core.spec.FactSpec;
import org.junit.Test;

public class FluentSubscriptionRequestTest {

    @Test
    public void testSinceSubscription() throws Exception {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo")).sinceSubscription();

        assertTrue(r.ephemeral());
    }

    @Test(expected = NullPointerException.class)
    public void testSinceNull() throws Exception {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo")).since(null);
    }

    @Test(expected = NullPointerException.class)
    public void testFollowNull() throws Exception {
        SubscriptionRequest.follow(null);
    }

    @Test(expected = NullPointerException.class)
    public void testCatchupNull() throws Exception {
        SubscriptionRequest.catchup(null);
    }

    @Test(expected = NullPointerException.class)
    public void testOrNull() throws Exception {
        SubscriptionRequest.catchup(FactSpec.forMark()).or(null);
    }

    @Test
    public void testToString() throws Exception {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.forMark()).sinceInception();
        assertSame(r.debugInfo(), r.toString());
    }

    @Test
    public void testDebugInfo() throws Exception {
        String debugInfo = SubscriptionRequest.catchup(FactSpec.forMark()).sinceInception()
                .debugInfo();
        assertNotNull(debugInfo);
        assertTrue(debugInfo.contains(this.getClass().getSimpleName()));
        assertTrue(debugInfo.contains("testDebugInfo")); // method name
    }

}
