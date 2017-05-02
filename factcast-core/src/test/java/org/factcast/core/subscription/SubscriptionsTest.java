package org.factcast.core.subscription;

import org.junit.Before;
import org.junit.Test;

public class SubscriptionsTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test(expected = NullPointerException.class)
    public void testOn() throws Exception {
        Subscriptions.on(null);
    }

}
