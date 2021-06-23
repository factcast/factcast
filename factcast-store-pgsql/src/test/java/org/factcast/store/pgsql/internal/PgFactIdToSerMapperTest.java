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
package org.factcast.store.pgsql.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.UUID;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.store.pgsql.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.test.IntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
public class PgFactIdToSerMapperTest {

  @Autowired JdbcTemplate tpl;

  @Autowired FactStore store;

  @Autowired PgMetrics metrics;

  @Test
  void testRetrieve() {
    Fact m = Fact.builder().buildWithoutPayload();
    store.publish(Collections.singletonList(m));
    long retrieve = new PgFactIdToSerialMapper(tpl, metrics).retrieve(m.id());
    assertTrue(retrieve > 0);
  }

  @Test
  void testRetrieveNonExistant() {
    try {
      new PgFactIdToSerialMapper(tpl, metrics)
          .retrieve(UUID.fromString("2b86d90e-2755-4f82-b86d-fd092b25ccc8"));
      fail();
    } catch (Throwable ignored) {
    }
  }
}
