package org.factcast.core.subscription;

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
}
