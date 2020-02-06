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
package org.factcast.store.pgsql.internal.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

@ExtendWith(MockitoExtension.class)
public class PgLatestSerialFetcherTest {
    @InjectMocks
    PgLatestSerialFetcher uut;

    @Mock
    JdbcTemplate jdbc;

    @Mock
    SqlRowSet rs;

    @Test
    public void testRetrieveLatestSerRetuns0WhenExceptionThrown() throws Exception {
        when(jdbc.queryForRowSet(anyString())).thenThrow(UnsupportedOperationException.class);
        assertEquals(0, uut.retrieveLatestSer());
    }

    @Test
    public void shouldReturn0IfNotFound() throws Exception {
        when(jdbc.queryForRowSet(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        assertEquals(0, uut.retrieveLatestSer());
    }

}
