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
package org.factcast.store.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.*;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
public class PgLatestSerialFetcherTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  private PgLatestSerialFetcher uut;

  @Test
  void testRetrieveLatestSer() {
    uut = new PgLatestSerialFetcher(jdbcTemplate);
    assertEquals(0, uut.retrieveLatestSer());
    assertEquals(0, uut.retrieveLatestSer());
    jdbcTemplate.execute(
        "INSERT INTO "
            + PgConstants.TABLE_FACT
            + "("
            + PgConstants.COLUMN_HEADER
            + ","
            + PgConstants.COLUMN_PAYLOAD
            + ") VALUES('{\"id\":\""
            + UUID.randomUUID()
            + "\", \"ns\":\"hups\"}','{}') ");
    assertEquals(1, uut.retrieveLatestSer());
    jdbcTemplate.execute(
        "INSERT INTO "
            + PgConstants.TABLE_FACT
            + "("
            + PgConstants.COLUMN_HEADER
            + ","
            + PgConstants.COLUMN_PAYLOAD
            + ") VALUES('{\"id\":\""
            + UUID.randomUUID()
            + "\", \"ns\":\"hups\"}','{}') ");
    jdbcTemplate.execute(
        "INSERT INTO "
            + PgConstants.TABLE_FACT
            + "("
            + PgConstants.COLUMN_HEADER
            + ","
            + PgConstants.COLUMN_PAYLOAD
            + ") VALUES('{\"id\":\""
            + UUID.randomUUID()
            + "\", \"ns\":\"hups\"}','{}') ");
    assertEquals(3, uut.retrieveLatestSer());
  }

  @Test
  void testRetrieveLatestSerWithException() {
    JdbcTemplate jdbcMock = mock(JdbcTemplate.class);
    when(jdbcMock.queryForRowSet(anyString())).thenThrow(new EmptyResultDataAccessException(1));
    uut = new PgLatestSerialFetcher(jdbcMock);
    assertEquals(0, uut.retrieveLatestSer());
  }
}
