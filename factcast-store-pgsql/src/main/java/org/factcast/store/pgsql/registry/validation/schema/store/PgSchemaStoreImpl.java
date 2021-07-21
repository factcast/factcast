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
package org.factcast.store.pgsql.registry.validation.schema.store;

import io.micrometer.core.instrument.Tags;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.map.LRUMap;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics.EVENT;
import org.factcast.store.pgsql.registry.validation.schema.SchemaConflictException;
import org.factcast.store.pgsql.registry.validation.schema.SchemaKey;
import org.factcast.store.pgsql.registry.validation.schema.SchemaSource;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;
import org.springframework.jdbc.core.JdbcTemplate;

/** @author uwe */
@RequiredArgsConstructor
public class PgSchemaStoreImpl implements SchemaStore {

  private final Map<SchemaKey, String> nearCache = new LRUMap<>(500);

  @NonNull private final JdbcTemplate jdbcTemplate;

  @NonNull private final RegistryMetrics registryMetrics;

  @Override
  public void register(@NonNull SchemaSource key, @NonNull String schema)
      throws SchemaConflictException {
    nearCache.put(key.toKey(), schema);
    jdbcTemplate.update(
        "INSERT INTO schemastore (id,hash,ns,type,version,jsonschema) VALUES (?,?,?,?,?,? :: JSONB) "
            + "ON CONFLICT ON CONSTRAINT schemastore_pkey DO "
            + "UPDATE set hash=?,ns=?,type=?,version=?,jsonschema=? :: JSONB WHERE schemastore.id=?",
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
            EVENT.SCHEMA_CONFLICT, Tags.of(RegistryMetrics.TAG_IDENTITY_KEY, key.id()));
        throw new SchemaConflictException("Key " + key + " does not match the stored hash " + hash);
      }
    } else {
      return false;
    }
  }

  @Override
  public synchronized Optional<String> get(@NonNull SchemaKey key) {
    return Optional.ofNullable(
        nearCache.computeIfAbsent(
            key,
            k -> {
              List<String> schema =
                  jdbcTemplate.queryForList(
                      "SELECT jsonschema FROM schemastore WHERE ns=? AND type=? AND version=? ",
                      String.class,
                      key.ns(),
                      key.type(),
                      key.version());

              if (!schema.isEmpty()) {
                return schema.get(0);
              } else {
                return (String) null;
              }
            }));
  }
}
