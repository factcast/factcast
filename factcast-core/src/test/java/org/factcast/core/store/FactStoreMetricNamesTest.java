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
package org.factcast.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FactStoreMetricNamesTest {

    @Test
    void testFactStoreMetricNames() {
        final String type = "something";
        FactStoreMetricNames n = new FactStoreMetricNames(type);
        final String typedPrefix = "factstore." + type + ".";
        assertTrue(n.factPublishingMeter().startsWith(typedPrefix));
        assertTrue(n.factPublishingFailed().startsWith(typedPrefix));
        assertTrue(n.factPublishingLatency().startsWith(typedPrefix));
        assertTrue(n.fetchLatency().startsWith(typedPrefix));
        assertTrue(n.connectionFailure().startsWith(typedPrefix));
        assertTrue(n.subscribeCatchup().startsWith(typedPrefix));
        assertTrue(n.subscribeFollow().startsWith(typedPrefix));
        assertEquals(type, n.type());
    }

    @Test
    void testFactStoreMetricNamesNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new FactStoreMetricNames(null);
        });
    }
}
