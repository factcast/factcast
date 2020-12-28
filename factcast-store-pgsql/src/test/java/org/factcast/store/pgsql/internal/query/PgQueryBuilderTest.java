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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import org.assertj.core.util.Lists;
import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PgQueryBuilderTest {

  @Nested
  class WhenCreatingStatementSetter {
    @Mock private @NonNull AtomicLong serial;

    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void happyPath() {
      Mockito.when(serial.get()).thenReturn(120L);
      val spec1 = FactSpec.ns("ns1").type("t1").meta("foo", "bar").aggId(new UUID(0, 1));
      val spec2 = FactSpec.ns("ns2").type("t2").meta("foo", "bar");
      val spec3 = FactSpec.ns("ns3");
      val specs = Lists.newArrayList(spec1, spec2, spec3);
      val underTest = new PgQueryBuilder(specs);
      val setter = underTest.createStatementSetter(serial);
      val ps = mock(PreparedStatement.class);

      setter.setValues(ps);

      int index = 0;
      // first spec
      verify(ps).setString(++index, "{\"ns\": \"ns1\"}");
      verify(ps).setString(++index, "{\"type\": \"t1\"}");
      verify(ps).setString(++index, "{\"aggIds\": [\"00000000-0000-0000-0000-000000000001\"]}");
      verify(ps).setString(++index, "{\"meta\":{\"foo\":\"bar\"}}");

      // 2nd spec
      verify(ps).setString(++index, "{\"ns\": \"ns2\"}");
      verify(ps).setString(++index, "{\"type\": \"t2\"}");
      verify(ps).setString(++index, "{\"meta\":{\"foo\":\"bar\"}}");
      // 3rd spec
      verify(ps).setString(++index, "{\"ns\": \"ns3\"}");

      // ser>?
      verify(ps).setLong(++index, 120);
      verifyNoMoreInteractions(ps);
    }
  }

  @Nested
  class WhenCreatingSQL {
    @BeforeEach
    void setup() {}

    @Test
    void happyPath() {
      val spec1 = FactSpec.ns("ns1").type("t1").meta("foo", "bar").aggId(new UUID(0, 1));
      val spec2 = FactSpec.ns("ns2").type("t2").meta("foo", "bar");
      val specs = Lists.newArrayList(spec1, spec2);
      val underTest = new PgQueryBuilder(specs);
      val sql = underTest.createSQL();

      // projection
      assertThat(sql)
          .startsWith(
              "SELECT ser, header, payload, header->>'id' AS id, header->>'aggIds' AS aggIds,"
                  + " header->>'ns' AS ns, header->>'type' AS type, header->>'version' AS version FROM fact");

      // where clause for two specs
      val expectedSpec1 =
          "(1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb)";
      val expectedSpec2 =
          "(1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb)"; // no aggid
      assertThat(sql).contains("( " + expectedSpec1 + " OR " + expectedSpec2 + " )");
      assertThat(sql).endsWith("AND ser>? ORDER BY ser ASC");
    }
  }

  @Nested
  class WhenCreatingStateSQL {
    @BeforeEach
    void setup() {}

    @Test
    void happyPath() {
      val spec1 = FactSpec.ns("ns1").type("t1").meta("foo", "bar").aggId(new UUID(0, 1));
      val spec2 = FactSpec.ns("ns2").type("t2").meta("foo", "bar");
      val specs = Lists.newArrayList(spec1, spec2);
      val underTest = new PgQueryBuilder(specs);
      val sql = underTest.createStateSQL();

      // projection
      assertThat(sql).startsWith("SELECT ser FROM fact");

      // where clause for two specs
      val expectedSpec1 =
          "(1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb)";
      val expectedSpec2 =
          "(1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb)"; // no aggid
      assertThat(sql).contains("( " + expectedSpec1 + " OR " + expectedSpec2 + " )");
      assertThat(sql).endsWith(" ORDER BY ser DESC LIMIT 1");
    }
  }

  @Nested
  class WhenCatchupingSQL {
    @BeforeEach
    void setup() {}

    @Test
    void happyPath() {
      val spec1 = FactSpec.ns("ns1").type("t1").meta("foo", "bar").aggId(new UUID(0, 1));
      val spec2 = FactSpec.ns("ns2").type("t2").meta("foo", "bar");
      val specs = Lists.newArrayList(spec1, spec2);
      val underTest = new PgQueryBuilder(specs);
      val sql = underTest.catchupSQL();

      // projection
      assertThat(sql).startsWith("INSERT INTO catchup (ser) (SELECT ser FROM fact");

      // where clause for two specs
      val expectedSpec1 =
          "(1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb)";
      val expectedSpec2 =
          "(1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb)"; // no aggid
      assertThat(sql).contains("( " + expectedSpec1 + " OR " + expectedSpec2 + " )");
    }
  }
}
