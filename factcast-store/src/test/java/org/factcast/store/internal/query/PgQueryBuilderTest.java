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
package org.factcast.store.internal.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PgQueryBuilderTest {

  @Nested
  class WhenCreatingStatementSetter {
    @Mock private @NonNull AtomicLong serial;
    @Mock CurrentStatementHolder holder;

    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void happyPath() {
      Mockito.when(serial.get()).thenReturn(120L);
      var spec1 = FactSpec.ns("ns1").type("t1").meta("foo", "bar").aggId(new UUID(0, 1));
      var spec2 =
          FactSpec.ns("ns2").type("t2").meta("foo", "bar").metaExists("e").metaDoesNotExist("!e");
      var spec3 = FactSpec.ns("ns3");
      var spec4 = FactSpec.ns("ns4").aggId(new UUID(0, 1), new UUID(0, 2));
      var specs = Lists.newArrayList(spec1, spec2, spec3, spec4);
      var underTest = new PgQueryBuilder(specs);
      var setter = underTest.createStatementSetter(serial, null);
      var ps = mock(PreparedStatement.class);

      setter.setValues(ps);

      int index = 0;
      // first spec
      verify(ps).setString(++index, "{\"ns\": \"ns1\"}");
      verify(ps).setString(++index, "{\"type\": \"t1\"}");
      verify(ps).setString(++index, "{\"aggIds\": [\"00000000-0000-0000-0000-000000000001\"]}");
      verify(ps).setString(++index, "{\"meta\":{\"foo\":\"bar\"}}");
      verify(ps).setString(++index, "{\"meta\":{\"foo\":[\"bar\"]}}");

      // 2nd spec
      verify(ps).setString(++index, "{\"ns\": \"ns2\"}");
      verify(ps).setString(++index, "{\"type\": \"t2\"}");
      verify(ps).setString(++index, "{\"meta\":{\"foo\":\"bar\"}}");
      verify(ps).setString(++index, "{\"meta\":{\"foo\":[\"bar\"]}}");
      verify(ps).setString(++index, "$.\"meta\".\"e\"");
      verify(ps).setString(++index, "$.\"meta\".\"!e\""); // NOT is added in WHERE clause
      // before
      // 3rd spec
      verify(ps).setString(++index, "{\"ns\": \"ns3\"}");

      // 4th spec
      verify(ps).setString(++index, "{\"ns\": \"ns4\"}");
      verify(ps)
          .setString(
              ++index,
              "{\"aggIds\": [\"00000000-0000-0000-0000-000000000001\",\"00000000-0000-0000-0000-000000000002\"]}");

      // ser>?
      verify(ps).setLong(++index, serial.get());
      verifyNoMoreInteractions(ps);
    }

    @SneakyThrows
    @Test
    void setsCurrentStatement() {
      Mockito.when(serial.get()).thenReturn(120L);
      var underTest = new PgQueryBuilder(Lists.newArrayList(FactSpec.ns("ns3")), holder);
      var setter = underTest.createStatementSetter(serial, null);
      var ps = mock(PreparedStatement.class);

      setter.setValues(ps);

      verify(holder).statement(ps);
    }
  }

  @Nested
  class WhenCreatingSQL {

    @Test
    void happyPath() {
      var spec1 = FactSpec.ns("ns1").type("t1").meta("foo", "bar").aggId(new UUID(0, 1));
      var spec2 = FactSpec.ns("ns2").type("t2").meta("foo", "bar");
      var specs = Lists.newArrayList(spec1, spec2);
      var underTest = new PgQueryBuilder(specs);
      var sql = underTest.createSQL(null);

      assertThat(sql)
          .isEqualTo(
              "SELECT ser, header, payload, header->>'id' AS id, header->>'aggIds' AS aggIds, header->>'ns' AS ns, header->>'type' AS type, header->>'version' AS version FROM fact WHERE ( (1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb AND (header @> ?::jsonb OR header @> ?::jsonb)) OR (1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND (header @> ?::jsonb OR header @> ?::jsonb)) ) AND (ser>?) ORDER BY ser ASC");
    }

    @Test
    void happyPathWithMetaExists() {
      var spec1 =
          FactSpec.ns("ns1")
              .type("t1")
              .meta("foo", "bar")
              .aggId(new UUID(0, 1))
              .metaExists("mustExist")
              .metaDoesNotExist("mustNotExist");
      var spec2 = FactSpec.ns("ns2").type("t2").meta("foo", "bar");
      var specs = Lists.newArrayList(spec1, spec2);
      var underTest = new PgQueryBuilder(specs);
      var sql = underTest.createSQL(null);

      assertThat(sql)
          .isEqualTo(
              "SELECT ser, header, payload, header->>'id' AS id, header->>'aggIds' AS aggIds, header->>'ns' AS ns, header->>'type' AS type, header->>'version' AS version FROM fact WHERE ( (1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb AND (header @> ?::jsonb OR header @> ?::jsonb) AND jsonb_path_exists(header, ?::jsonpath) AND NOT jsonb_path_exists(header, ?::jsonpath)) OR (1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND (header @> ?::jsonb OR header @> ?::jsonb)) ) AND (ser>?) ORDER BY ser ASC");
    }
  }

  @Nested
  class WhenCreatingStateSQL {

    @Test
    void happyPath() {
      var spec1 = FactSpec.ns("ns1").type("t1").meta("foo", "bar").aggId(new UUID(0, 1));
      var spec2 = FactSpec.ns("ns2").type("t2").meta("foo", "bar");
      var spec3 = FactSpec.ns("ns3").type("t3").aggId(new UUID(0, 1), new UUID(0, 2));
      var specs = Lists.newArrayList(spec1, spec2, spec3);
      var underTest = new PgQueryBuilder(specs);
      var sql = underTest.createStateSQL(null);

      // projection
      assertThat(sql).startsWith("SELECT ser FROM fact");

      // where clause for two specs
      var expectedSpec1 =
          "(1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb AND (header @> ?::jsonb OR header @> ?::jsonb))";
      var expectedSpec2 =
          "(1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND (header @> ?::jsonb OR header @> ?::jsonb))"; // no aggid
      var expectedSpec3 =
          "(1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb)"; // no meta,
      // multi
      // aggid
      assertThat(sql)
          .contains("( " + expectedSpec1 + " OR " + expectedSpec2 + " OR " + expectedSpec3 + " )")
          .endsWith(" ORDER BY ser DESC LIMIT 1");
    }
  }

  @Nested
  class WhenCatchupingSQL {

    @Test
    void happyPath() {
      var spec1 = FactSpec.ns("ns1").type("t1").meta("foo", "bar").aggId(new UUID(0, 1));
      var spec2 = FactSpec.ns("ns2").type("t2").meta("foo", "bar");
      var spec3 = FactSpec.ns("ns3").type("t3").aggId(new UUID(0, 1), new UUID(0, 2));
      var specs = Lists.newArrayList(spec1, spec2, spec3);
      var underTest = new PgQueryBuilder(specs);
      var sql = underTest.catchupSQL();

      // projection
      assertThat(sql).startsWith("INSERT INTO catchup (ser) (SELECT ser FROM fact");

      // where clause for two specs
      var expectedSpec1 =
          "(1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb AND (header @>"
              + " ?::jsonb OR header @> ?::jsonb))";
      var expectedSpec2 =
          "(1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND (header @> ?::jsonb OR header @> ?::jsonb))"; // no aggid
      var expectedSpec3 =
          "(1=1 AND header @> ?::jsonb AND header @> ?::jsonb AND header @> ?::jsonb)"; // no meta,
      // multi
      // aggid
      assertThat(sql)
          .contains("( " + expectedSpec1 + " OR " + expectedSpec2 + " OR " + expectedSpec3 + " )");
    }
  }
}
