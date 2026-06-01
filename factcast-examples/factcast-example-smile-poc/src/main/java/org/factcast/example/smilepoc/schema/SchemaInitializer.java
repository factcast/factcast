/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.example.smilepoc.schema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaInitializer {

  private final JdbcTemplate jdbc;

  public void reset() {
    log.info("Dropping tables");
    jdbc.execute("DROP TABLE IF EXISTS transformationcache_jsonb");
    jdbc.execute("DROP TABLE IF EXISTS transformationcache_smile");
    ensureExists();
  }

  public void ensureExists() {
    // Variant A — production shape, including the hash index from issue 4444.
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS transformationcache_jsonb (
          cache_key varchar PRIMARY KEY,
          header    jsonb  NOT NULL,
          payload   jsonb  NOT NULL
        )
        """);
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_jsonb_cache_key_hash "
            + "ON transformationcache_jsonb USING HASH (cache_key)");

    // Variant B — SMILE bytea + cache_key (with hash index) + promoted columns.
    // Keeps the 4444 read-path optimization while replacing JSONB path queries
    // (header->>'ns' etc.) and the LIKE-on-cache_key invalidation with proper indexes.
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS transformationcache_smile (
          cache_key varchar PRIMARY KEY,
          fact_id   uuid    NOT NULL,
          ns        varchar NOT NULL,
          type      varchar NOT NULL,
          data      bytea   NOT NULL
        )
        """);
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_smile_cache_key_hash "
            + "ON transformationcache_smile USING HASH (cache_key)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_smile_ns_type "
            + "ON transformationcache_smile (ns, type)");
    jdbc.execute(
        "CREATE INDEX IF NOT EXISTS idx_smile_fact_id " + "ON transformationcache_smile (fact_id)");
  }
}
