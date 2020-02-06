/*
 * Copyright © 2017-2020 factcast.org
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
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.internal.PgConstants;
import org.factcast.store.pgsql.internal.catchup.PgCatchUpFetchPage;
import org.factcast.store.pgsql.internal.rowmapper.PgIdFactExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

@ExtendWith(MockitoExtension.class)
public class PgCatchUpFetchPageTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private PgConfigurationProperties properties;

    @Mock
    private SubscriptionRequestTO req;

    private PgCatchUpFetchPage uut;

    @Test
    void testFetchIdFacts() {
        uut = new PgCatchUpFetchPage(jdbc, properties.getPageSize(), req, 12);
        uut.fetchIdFacts(new AtomicLong());
        verify(jdbc).query(eq(PgConstants.SELECT_ID_FROM_CATCHUP), any(
                PreparedStatementSetter.class), any(PgIdFactExtractor.class));
    }
}
