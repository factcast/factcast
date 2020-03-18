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
package org.factcast.store.pgsql.validation.schema.store;

import java.util.List;
import java.util.Optional;

import org.factcast.store.pgsql.validation.schema.SchemaConflictException;
import org.factcast.store.pgsql.validation.schema.SchemaKey;
import org.factcast.store.pgsql.validation.schema.SchemaSource;
import org.factcast.store.pgsql.validation.schema.SchemaStore;
import org.springframework.jdbc.core.JdbcTemplate;

import liquibase.integration.spring.SpringLiquibase;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * @author uwe
 *
 */
@RequiredArgsConstructor
public class PgSchemaStoreImpl implements SchemaStore {

    @NonNull
    private final JdbcTemplate jdbcTemplate;

    // note that SpringLiquibase needs to be injected in order to make sure it
    // is initialized before we're intentionally not using @DependsOn here,
    // as a change of the beanname within liquibase would break our code
    private final SpringLiquibase unused;

    @Override
    public void register(@NonNull SchemaSource key, @NonNull String schema)
            throws SchemaConflictException {
        jdbcTemplate.update(
                "INSERT INTO schemastore (id,hash,ns,type,version,jsonschema) VALUES (?,?,?,?,?,?) ",
                key.id(), key.hash(), key.ns(), key.type(), key.version(), schema);
    }

    @Override
    public boolean contains(@NonNull SchemaSource key) throws SchemaConflictException {
        List<String> hashes = jdbcTemplate.queryForList("SELECT hash FROM schemastore WHERE id=?",
                String.class,
                key.id());
        if (!hashes.isEmpty()) {
            String hash = hashes.get(0);
            if (hash.equals(key.hash()))
                return true;
            else
                throw new SchemaConflictException("Key " + key + " does not match the stored hash "
                        + hash);
        } else
            return false;
    }

    @Override
    public synchronized Optional<String> get(@NonNull SchemaKey key) {
        List<String> schema = jdbcTemplate.queryForList(
                "SELECT jsonschema FROM schemastore WHERE ns=? AND type=? AND version=? ",
                String.class, key.ns(),
                key.type(), key.version());
        if (!schema.isEmpty()) {
            return Optional.of(schema.get(0));
        } else
            return Optional.empty();
    }

}
