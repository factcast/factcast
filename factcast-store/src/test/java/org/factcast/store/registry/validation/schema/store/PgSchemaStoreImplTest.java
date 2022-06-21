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
package org.factcast.store.registry.validation.schema.store;

import java.sql.SQLException;
import java.util.*;

import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.factcast.store.registry.validation.schema.SchemaSource;
import org.factcast.store.registry.validation.schema.SchemaStore;
import org.factcast.store.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.mockito.Mockito.*;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@ExtendWith(SpringExtension.class)
@ExtendWith(MockitoExtension.class)
@IntegrationTest
public class PgSchemaStoreImplTest extends AbstractSchemaStoreTest {

  @Autowired private JdbcTemplate tpl;
  @Mock private JdbcTemplate mockTpl;

  @Override
  protected SchemaStore createUUT() {
    return new PgSchemaStoreImpl(tpl, registryMetrics);
  }

  @Test
  void doesNotRefetch() {
    // first goes to DB

    var uut = new PgSchemaStoreImpl(mockTpl, registryMetrics);

    SchemaKey key = mock(SchemaKey.class);
    when(mockTpl.queryForList(any(), eq(String.class), same(null), same(null), eq(0)))
        .thenReturn(Collections.singletonList("my schema"));

    uut.get(key);

    verify(mockTpl, times(1)).queryForList(any(), eq(String.class), same(null), same(null), eq(0));
    verifyNoMoreInteractions(mockTpl);

    // now fetch again - should be answered from the near cache

    uut.get(key);
    verifyNoMoreInteractions(mockTpl);
  }

  @Test
  void cachesRegistrations() {
    // first goes to DB

    var uut = new PgSchemaStoreImpl(mockTpl, registryMetrics);

    SchemaSource source = new SchemaSource().hash("hash").id("id").ns("ns").type("type");
    uut.register(source, "foo");
    verify(mockTpl)
        .update(
            "INSERT INTO schemastore (id,hash,ns,type,version,jsonschema) VALUES (?,?,?,?,?,? ::"
                + " JSONB) ON CONFLICT ON CONSTRAINT schemastore_pkey DO UPDATE set"
                + " hash=?,ns=?,type=?,version=?,jsonschema=? :: JSONB WHERE schemastore.id=?",
            "id",
            "hash",
            "ns",
            "type",
            0,
            "foo",
            "hash",
            "ns",
            "type",
            0,
            "foo",
            "id");
    verifyNoMoreInteractions(mockTpl);

    // now fetch again - should be answered from the near cache

    uut.get(source.toKey());
    verifyNoMoreInteractions(mockTpl);
  }

  @Test
  void retriesOnWrongConstrainConflict() {
    var uut = new PgSchemaStoreImpl(mockTpl, registryMetrics);

    SchemaSource source = new SchemaSource().hash("hash").id("id").ns("ns").type("type");

    when(mockTpl.update(
            "INSERT INTO schemastore (id,hash,ns,type,version,jsonschema) VALUES (?,?,?,?,?,? ::"
                + " JSONB) ON CONFLICT ON CONSTRAINT schemastore_pkey DO UPDATE set"
                + " hash=?,ns=?,type=?,version=?,jsonschema=? :: JSONB WHERE schemastore.id=?",
            "id",
            "hash",
            "ns",
            "type",
            0,
            "foo",
            "hash",
            "ns",
            "type",
            0,
            "foo",
            "id"))
        .thenThrow(
            new DataAccessException("oh my", new SQLException("bad things happened")) {
              private static final long serialVersionUID = 6190462075599395409L;
            });
    uut.register(source, "foo");
    verify(mockTpl)
        .update(
            "INSERT INTO schemastore (id,hash,ns,type,version,jsonschema) VALUES (?,?,?,?,?,? ::"
                + " JSONB) ON CONFLICT ON CONSTRAINT schemastore_ns_type_version_key DO UPDATE set"
                + " hash=?,ns=?,type=?,version=?,jsonschema=? :: JSONB WHERE schemastore.id=?",
            "id",
            "hash",
            "ns",
            "type",
            0,
            "foo",
            "hash",
            "ns",
            "type",
            0,
            "foo",
            "id");
    verifyNoMoreInteractions(mockTpl);
  }
}
