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
package org.factcast.store.inmem;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.store.inmem.InMemFactStore.AfterPredicate;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class AfterPredicateTest {

    AfterPredicate uut = new AfterPredicate(new UUID(0L, 3L));

    @Test
    void testStatefulFiltering() throws Exception {

        assertFalse(uut.test(Fact.builder().id(new UUID(0L, 1)).build("{}")));
        assertFalse(uut.test(Fact.builder().id(new UUID(0L, 2)).build("{}")));
        assertFalse(uut.test(Fact.builder().id(new UUID(0L, 3)).build("{}")));
        assertTrue(uut.test(Fact.builder().id(new UUID(0L, 4)).build("{}")));
        // now that the flip is switched, anything should go through
        assertTrue(uut.test(Fact.builder().id(new UUID(0L, 1)).build("{}")));

    }
}
