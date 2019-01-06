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
package org.factcast.store.pgsql.internal.catchup;

import static org.junit.jupiter.api.Assertions.*;

import org.factcast.core.subscription.SubscriptionRequestTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
public class PGCatchUpFetchPageTest {

    @Mock
    SubscriptionRequestTO req;

    @Mock
    JdbcTemplate jdbc;

    @Test
    public void testNullParameterContracts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new PGCatchUpFetchPage(null, 10, req, 1);
        });
        assertThrows(NullPointerException.class, () -> {
            new PGCatchUpFetchPage(jdbc, 10, null, 1);
        });
        PGCatchUpFetchPage uut = new PGCatchUpFetchPage(jdbc, 10, req, 1);
        assertThrows(NullPointerException.class, () -> {
            uut.fetchFacts(null);
        });
        assertThrows(NullPointerException.class, () -> {
            uut.fetchIdFacts(null);
        });
    }

}
