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
package org.factcast.itests.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.store.registry.transformation.cache.PgTransformationCache;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.factcast.test.IntegrationTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.jupiter.api.Assertions.assertNull;

@RunWith(SpringRunner.class)
@SpringBootTest
@IntegrationTest
public class TransformationCacheTest {

  @Autowired FactCast fc;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Autowired
  TransformationCache transformationCache;

  @Test
  public void transformationCacheInvalidation() throws Exception {
    UUID id = UUID.randomUUID();
    Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
    fc.publish(f);

    fc.fetchByIdAndVersion(id, 2).orElse(null);
    fc.fetchByIdAndVersion(id, 3).orElse(null);
    ((PgTransformationCache) transformationCache).flush();
    Thread.sleep(100); // TODO flaky

    printTransformationStoreContent();

    System.out.println("### After transformation cache flush");
    printTransformationCacheContent();

    jdbcTemplate.update(String.format("DELETE FROM transformationstore WHERE type='%s' AND from_version=%d", f.type(), 1));
    Thread.sleep(2000); // TODO flaky again

    System.out.println("### After transformation delete");
    printTransformationStoreContent();
    printTransformationCacheContent();

    // add proxy

    assertNull(fc.fetchByIdAndVersion(id, 3).orElse(null));
  }

  private Fact createTestFact(UUID id, int version, String body) {
    return Fact.builder().ns("users").type("UserCreated").id(id).version(version).build(body);
  }

  private void printTransformationCacheContent() {
    System.out.println("### Transformation cache content:");
    List<String> cacheRows = jdbcTemplate.query("SELECT * FROM transformationcache", new TransformationCacheRowMapper());
    cacheRows.forEach(System.out::println);
  }

  private void printTransformationStoreContent() {
    System.out.println("### Transformation store content:");
    List<String> storeRows = jdbcTemplate.query("SELECT * FROM transformationstore", new TransformationStoreRowMapper());
    storeRows.forEach(System.out::println);
  }

  private class TransformationCacheRowMapper implements RowMapper<String> {
    @Override
    public String mapRow(ResultSet rs, int rowNum) throws SQLException {
      return rs.getString("header");
    }
  }

  private class TransformationStoreRowMapper implements RowMapper<String> {
    @Override
    public String mapRow(ResultSet rs, int rowNum) throws SQLException {
      return String.format("%s.%s %d->%d", rs.getString("ns"), rs.getString("type"), rs.getInt("from_version"), rs.getInt("to_version"));
    }
  }
}
