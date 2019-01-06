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
package org.factcast.store.pgsql.internal.catchup.paged;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.PGConfigurationProperties;
import org.factcast.store.pgsql.internal.PGConstants;
import org.factcast.store.pgsql.internal.catchup.PGCatchUpFetchPage;
import org.factcast.store.pgsql.internal.rowmapper.PGIdFactExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

@ExtendWith(MockitoExtension.class)
public class PGCatchUpFetchPageTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private PGConfigurationProperties properties;

    @Mock
    private SubscriptionRequestTO req;

    private PGCatchUpFetchPage uut;

    @Test
    void testFetchIdFacts() {
        uut = new PGCatchUpFetchPage(jdbc, properties.getPageSize(), req, 12);
        uut.fetchIdFacts(new AtomicLong());
        verify(jdbc).query(eq(PGConstants.SELECT_ID_FROM_CATCHUP), any(
                PreparedStatementSetter.class), any(PGIdFactExtractor.class));
    }
}
