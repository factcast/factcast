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

import java.util.*;

import org.assertj.core.api.Assertions;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.State;
import org.factcast.core.store.TokenStore;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.test.AbstractTokenStoreTest;
import org.factcast.store.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@ExtendWith(SpringExtension.class)
@IntegrationTest
public class PgTokenStoreTest extends AbstractTokenStoreTest {

  @Autowired PgMetrics metrics;
  @Autowired JdbcTemplate jdbc;

  @Override
  protected TokenStore createTokenStore() {
    jdbc = Mockito.spy(jdbc);
    return new PgTokenStore(jdbc, metrics);
  }

  @Test
  public void testStateJsonSerializable() throws Exception {

    State s = new State();
    s.serialOfLastMatchingFact(99);
    List<FactSpec> specs =
        Arrays.asList(
            FactSpec.ns("foo").aggId(new UUID(0, 1)), FactSpec.ns("bar").type("someType"));
    s.specs(specs);

    String json = FactCastJson.writeValueAsString(s);

    Assertions.assertThat(json)
        .isEqualTo(
            "{\"specs\":[{\"ns\":\"foo\",\"type\":null,\"version\":0,\"aggId\":\"00000000-0000-0000-0000-000000000001\",\"meta\":{},\"jsFilterScript\":null,\"filterScript\":null},{\"ns\":\"bar\",\"type\":\"someType\",\"version\":0,\"aggId\":null,\"meta\":{},\"jsFilterScript\":null,\"filterScript\":null}],\"serialOfLastMatchingFact\":99}");
  }

  @Test
  void compaction() {
    uut.compact();
    Mockito.verify(jdbc).update(PgConstants.COMPACT_TOKEN);
  }
}
