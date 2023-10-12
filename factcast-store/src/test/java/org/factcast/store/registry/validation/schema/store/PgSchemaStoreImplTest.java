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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import nl.altindag.log.LogCaptor;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.registry.validation.schema.SchemaSource;
import org.factcast.store.registry.validation.schema.SchemaStore;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@ExtendWith(SpringExtension.class)
@ExtendWith(MockitoExtension.class)
@IntegrationTest
public class PgSchemaStoreImplTest extends AbstractSchemaStoreTest {

  @Autowired private JdbcTemplate tpl;
  @Mock private JdbcTemplate mockTpl;

  @Mock private StoreConfigurationProperties storeConfigurationProperties;

  @Override
  protected SchemaStore createUUT() {
    return new PgSchemaStoreImpl(tpl, registryMetrics, storeConfigurationProperties);
  }

  @Test
  void retriesOnWrongConstrainConflict() {
    var uut = new PgSchemaStoreImpl(mockTpl, registryMetrics, storeConfigurationProperties);

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

  @Test
  void skipsInsertIfReadOnlyMode() {
    when(storeConfigurationProperties.isReadOnlyModeEnabled()).thenReturn(true);

    var uut = new PgSchemaStoreImpl(mockTpl, registryMetrics, storeConfigurationProperties);

    SchemaSource source = new SchemaSource().hash("hash").id("id").ns("ns").type("type");

    try (var logs = LogCaptor.forClass(PgSchemaStoreImpl.class)) {
      uut.register(source, "foo");

      assertThat(logs.getInfoLogs()).contains("Skipping schema registration in read-only mode");
    }

    verifyNoInteractions(mockTpl);
  }
}
