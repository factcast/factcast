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
package org.factcast.store.registry.transformation.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Date;
import java.util.UUID;
import org.factcast.core.Fact;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@ExtendWith(MockitoExtension.class)
@IntegrationTest
class PgTransformationCacheTest extends AbstractTransformationCacheTest {
  @Autowired private JdbcTemplate tpl;

  @Override
  protected TransformationCache createUUT() {
    return new PgTransformationCache(tpl, registryMetrics);
  }

  @Test
  void testLastAccessUpdateAfterFind() {
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
    String chainId = "1-2-3";

    uut.put(fact, chainId);

    Date dateOnInsert = getLastAccessDate();

    uut.find(fact.id(), fact.version(), chainId);

    Date dateAfterUpdate = getLastAccessDate();

    assertTrue(dateOnInsert.before(dateAfterUpdate));
  }

  private Date getLastAccessDate() {
    return tpl.query(
            "SELECT last_access FROM transformationcache",
            new Object[] {},
            (rs, rowNum) -> rs.getObject("last_access", Date.class))
        .get(0);
  }
}
