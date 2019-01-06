/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
