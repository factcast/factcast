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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.concurrent.*;
import lombok.SneakyThrows;
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
@ExtendWith(SpringExtension.class)
@ExtendWith(MockitoExtension.class)
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@IntegrationTest
class PgTransformationCacheTest extends AbstractTransformationCacheTest {
  @Autowired private JdbcTemplate tpl;

  private final List<String> buffer = new ArrayList<String>();
  private final int maxBufferSize = 10;
  private final CountDownLatch wasflushed = new CountDownLatch(1);

  @Override
  protected TransformationCache createUUT() {
    return new PgTransformationCache(tpl, registryMetrics, buffer, maxBufferSize) {
      @Override
      public void flush() {
        super.flush();
        wasflushed.countDown();
      }
    };
  }

  @Test
  void testAddToBatchAfterFind() {
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
    String chainId = "1-2-3";
    String cacheKey = CacheKey.of(fact, chainId);
    uut.put(fact, chainId);
    assertThat(buffer).isEmpty(); // not touched by a put

    uut.find(fact.id(), fact.version(), chainId);

    assertThat(buffer).contains(cacheKey);
  }

  @SneakyThrows
  @Test
  void testFlushBatchAfterFind() {
    for (int i = 0; i < maxBufferSize; i++) {
      Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
      String chainId = String.valueOf(i);
      uut.put(fact, chainId);

      uut.find(fact.id(), fact.version(), chainId);
    }
    // flush is async
    wasflushed.await();

    assertThat(buffer).isEmpty();
  }

  @Test
  void testBatchUpdateAfterFind() {
    for (int i = 0; i < maxBufferSize; i++) {
      Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
      String chainId = String.valueOf(i);
      uut.put(fact, chainId);
      buffer.add(CacheKey.of(fact, chainId));
    }
    Date maxDateOnInsert = getMaxLastAccessDate();

    ((PgTransformationCache) uut).flush();

    assertThat(getMinLastAccessDate()).isAfter(maxDateOnInsert);
  }

  private Date getMaxLastAccessDate() {
    return tpl.query(
            "SELECT last_access FROM transformationcache ORDER BY last_access DESC",
            new Object[] {},
            (rs, rowNum) -> rs.getObject("last_access", Date.class))
        .get(0);
  }

  private Date getMinLastAccessDate() {
    return tpl.query(
            "SELECT last_access FROM transformationcache ORDER BY last_access ASC",
            new Object[] {},
            (rs, rowNum) -> rs.getObject("last_access", Date.class))
        .get(0);
  }
}
