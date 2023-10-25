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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.*;
import nl.altindag.log.LogCaptor;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.factcast.store.registry.validation.schema.SchemaSource;
import org.factcast.store.registry.validation.schema.SchemaStore;
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
class PgSchemaStoreImplTest extends AbstractSchemaStoreTest {

  @Autowired private JdbcTemplate tpl;

  @Mock private StoreConfigurationProperties storeConfigurationProperties;

  @Override
  protected SchemaStore createUUT() {
    tpl.update("TRUNCATE schemastore");
    return new PgSchemaStoreImpl(tpl, registryMetrics, storeConfigurationProperties);
  }

  @Test
  void retriesOnWrongConstraintConflict() {
    var mockTpl = spy(tpl);
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
            "{}",
            "hash",
            "ns",
            "type",
            0,
            "{}",
            "id"))
        .thenThrow(
            new DataAccessException("oh my", new SQLException("bad things happened")) {
              private static final long serialVersionUID = 6190462075599395409L;
            });
    uut.register(source, "{}");
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
            "{}",
            "hash",
            "ns",
            "type",
            0,
            "{}",
            "id");
  }

  @Test
  void testGetAllSchemaKeys() {
    var mockTpl = spy(tpl);
    var uut = new PgSchemaStoreImpl(mockTpl, registryMetrics, storeConfigurationProperties);

    when(mockTpl.queryForList(PgSchemaStoreImpl.SELECT_NS_TYPE_VERSION))
        .thenReturn(
            List.of(
                Map.of("ns", "ns1", "type", "t1", "version", 1),
                Map.of("ns", "ns1", "type", "t1", "version", 2),
                Map.of("ns", "ns1", "type", "t2", "version", 1),
                Map.of("ns", "ns2", "type", "t3", "version", 1)));

    assertThat(uut.getAllSchemaKeys())
        .hasSize(4)
        .containsExactlyInAnyOrder(
            SchemaKey.of("ns1", "t1", 1),
            SchemaKey.of("ns1", "t1", 2),
            SchemaKey.of("ns1", "t2", 1),
            SchemaKey.of("ns2", "t3", 1));
  }

  @Test
  void skipsInsertIfReadOnlyMode() {
    var mockTpl = spy(tpl);
    when(storeConfigurationProperties.isReadOnlyModeEnabled()).thenReturn(true);

    var uut = new PgSchemaStoreImpl(mockTpl, registryMetrics, storeConfigurationProperties);

    SchemaSource source = new SchemaSource().hash("hash").id("id").ns("ns").type("type");

    try (var logs = LogCaptor.forClass(PgSchemaStoreImpl.class)) {
      uut.register(source, "{}");

      assertThat(logs.getInfoLogs()).contains("Skipping schema registration in read-only mode");
    }

    verifyNoInteractions(mockTpl);
  }
}
