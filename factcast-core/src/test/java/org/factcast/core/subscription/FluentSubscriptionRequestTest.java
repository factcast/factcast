package org.factcast.core.subscription;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FluentSubscriptionRequestTest {

    @Test
    void testFromSubscription() {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromNowOn();
        assertTrue(r.ephemeral());
    }

    @Test
    void testFromNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            SubscriptionRequest.catchup(FactSpec.ns("foo")).from(null);
        });
    }

    @Test
    void testFollowNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            SubscriptionRequest.follow((FactSpec) null);
        });
    }

    @Test
    void testCatchupNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            SubscriptionRequest.catchup((FactSpec) null);
        });
    }

    @Test
    void testOrNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            SubscriptionRequest.catchup(FactSpec.forMark()).or(null);
        });
    }

    @Test
    void testToString() {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.forMark()).fromScratch();
        assertSame(r.debugInfo(), r.toString());
    }

    @Test
    void testDebugInfo() {
        String debugInfo = SubscriptionRequest.catchup(FactSpec.forMark())
                .fromScratch()
                .debugInfo();
        assertNotNull(debugInfo);
        assertTrue(debugInfo.contains(this.getClass().getSimpleName()));
        // method name
        assertTrue(debugInfo.contains("testDebugInfo"));
    }
}
