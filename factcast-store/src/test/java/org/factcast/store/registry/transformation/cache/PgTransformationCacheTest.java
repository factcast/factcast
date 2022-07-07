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
import org.mockito.Mockito;
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

  private final int maxBufferSize = 10;
  private final CountDownLatch wasflushed = new CountDownLatch(1);
  private PgTransformationCache underTest;

  @Override
  protected TransformationCache createUUT() {
    this.underTest = Mockito.spy(new PgTransformationCache(tpl, registryMetrics, maxBufferSize));
    return underTest;
  }

  @Test
  void testAddToBatchAfterFind() {
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
    String chainId = "1-2-3";
    TransformationCache.Key cacheKey = TransformationCache.Key.of(fact, chainId);
    uut.put(cacheKey, fact);

    uut.find(TransformationCache.Key.of(fact.id(), fact.version(), chainId));

    assertThat(underTest.buffer()).containsKey(cacheKey);
    assertThat(underTest.buffer().get(cacheKey)).isNotNull();
  }

  @SneakyThrows
  @Test
  void testFlushBatchAfterFind() {
    Mockito.doAnswer(
            i -> {
              i.callRealMethod();
              wasflushed.countDown();
              return null;
            })
        .when(underTest)
        .flush();

    for (int i = 0; i < maxBufferSize; i++) {
      Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
      String chainId = String.valueOf(i);
      uut.put(TransformationCache.Key.of(fact, chainId), fact);

      uut.find(TransformationCache.Key.of(fact.id(), fact.version(), chainId));
    }
    // flush is async
    wasflushed.await();

    assertThat(underTest.buffer()).isEmpty();
  }
}
