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

import io.micrometer.core.instrument.Tags;
import java.util.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.validation.schema.SchemaConflictException;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.factcast.store.registry.validation.schema.SchemaSource;
import org.factcast.store.registry.validation.schema.SchemaStore;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author uwe
 */
@Slf4j
@RequiredArgsConstructor
public class PgSchemaStoreImpl implements SchemaStore {

  @NonNull private final JdbcTemplate jdbcTemplate;

  @NonNull private final RegistryMetrics registryMetrics;

  @NonNull private final StoreConfigurationProperties storeConfigurationProperties;

  @Override
  public void register(@NonNull SchemaSource key, @NonNull String schema)
      throws SchemaConflictException {

    if (storeConfigurationProperties.isReadOnlyModeEnabled()) {
      log.info("Skipping schema registration in read-only mode");
      return;
    }

    try {
      jdbcTemplate.update(
          "INSERT INTO schemastore (id,hash,ns,type,version,jsonschema) VALUES (?,?,?,?,?,? ::"
              + " JSONB) ON CONFLICT ON CONSTRAINT schemastore_pkey DO UPDATE set"
              + " hash=?,ns=?,type=?,version=?,jsonschema=? :: JSONB WHERE schemastore.id=?",
          // INSERT
          key.id(),
          key.hash(),
          key.ns(),
          key.type(),
          key.version(),
          schema,
          // UPDATE
          key.hash(),
          key.ns(),
          key.type(),
          key.version(),
          schema,
          key.id());
    } catch (DataAccessException exc) {
      // unfortunately, you can only react on ONE constraint with on conflict:
      // https://stackoverflow.com/questions/35888012/use-multiple-conflict-target-in-on-conflict-clause

      // as we have seen conflicts on the triple ns.type,version as well (which is surprising,
      // because pkey should complain first, we handle this situation by giving it another try here:
      jdbcTemplate.update(
          "INSERT INTO schemastore (id,hash,ns,type,version,jsonschema) VALUES (?,?,?,?,?,? ::"
              + " JSONB) ON CONFLICT ON CONSTRAINT schemastore_ns_type_version_key DO UPDATE set"
              + " hash=?,ns=?,type=?,version=?,jsonschema=? :: JSONB WHERE schemastore.id=?",
          // INSERT
          key.id(),
          key.hash(),
          key.ns(),
          key.type(),
          key.version(),
          schema,
          // UPDATE
          key.hash(),
          key.ns(),
          key.type(),
          key.version(),
          schema,
          key.id());
    }
  }

  @Override
  public boolean contains(@NonNull SchemaSource key) throws SchemaConflictException {

    // note: contains MUST always go to the DB in order to be able to detect hashing conflicts

    List<String> hashes =
        jdbcTemplate.queryForList(
            "SELECT hash FROM schemastore WHERE id=?", String.class, key.id());
    if (!hashes.isEmpty()) {
      String hash = hashes.get(0);
      if (hash.equals(key.hash())) {
        return true;
      } else {
        registryMetrics.count(
            RegistryMetrics.EVENT.SCHEMA_CONFLICT,
            Tags.of(RegistryMetrics.TAG_IDENTITY_KEY, key.id()));
        throw new SchemaConflictException("Key " + key + " does not match the stored hash " + hash);
      }
    } else {
      return false;
    }
  }

  @Override
  public Optional<String> get(@NonNull SchemaKey key) {
    List<String> schema =
        jdbcTemplate.queryForList(
            "SELECT jsonschema FROM schemastore WHERE ns=? AND type=? AND version=? ",
            String.class,
            key.ns(),
            key.type(),
            key.version());

    if (schema.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.ofNullable(schema.get(0));
    }
  }
}
