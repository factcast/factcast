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
package org.factcast.client.grpc;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;

import org.factcast.core.IdOnlyFact;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IdOnlyFactTest {

    @Test
    void testIdNonNull() {
        Assertions.assertThrows(NullPointerException.class, () -> new IdOnlyFact(null));
    }

    @Test
    void testNsUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> new IdOnlyFact(UUID.randomUUID()).ns());
    }

    @Test
    void testaggIdUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> new IdOnlyFact(UUID.randomUUID()).aggIds());
    }

    @Test
    void testtypeUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> new IdOnlyFact(UUID.randomUUID()).type());
    }

    @Test
    void testHeaderUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> new IdOnlyFact(UUID.randomUUID()).jsonHeader());
    }

    @Test
    void testPayloadUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> new IdOnlyFact(UUID.randomUUID()).jsonPayload());
    }

    @Test
    void testMetaUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> new IdOnlyFact(UUID.randomUUID()).meta("foo"));
    }

    @Test
    void testId() {
        UUID id = UUID.randomUUID();
        assertSame(id, new IdOnlyFact(id).id());
    }
}
