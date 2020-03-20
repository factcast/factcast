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
package org.factcast.store.pgsql.registry.transformation.store;

import java.util.List;
import java.util.Optional;

import org.factcast.store.pgsql.registry.transformation.SingleTransformation;
import org.factcast.store.pgsql.registry.transformation.Transformation;
import org.factcast.store.pgsql.registry.transformation.TransformationConflictException;
import org.factcast.store.pgsql.registry.transformation.TransformationKey;
import org.factcast.store.pgsql.registry.transformation.TransformationSource;
import org.springframework.jdbc.core.JdbcTemplate;

import liquibase.integration.spring.SpringLiquibase;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PgTransformationStoreImpl extends AbstractTransformationStore {
    @NonNull
    private final JdbcTemplate jdbcTemplate;

    // note that SpringLiquibase needs to be injected in order to make sure it
    // is initialized before we're intentionally not using @DependsOn here,
    // as a change of the beanname within liquibase would break our code
    private final SpringLiquibase unused;

    @Override
    protected void doRegister(@NonNull TransformationSource source, String transformation)
            throws TransformationConflictException {
        jdbcTemplate.update(
                "INSERT INTO transformationstore (id, hash, ns, type, from_version, to_version, transformation) VALUES (?,?,?,?,?,?,?)",
                source.id(), source.hash(), source.ns(), source.type(), source.from(), source.to(),
                transformation);
    }

    @Override
    public boolean contains(@NonNull TransformationSource source)
            throws TransformationConflictException {
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT hash FROM transformationstore WHERE id=?", String.class,
                source.id());

        if (!hashes.isEmpty()) {
            String hash = hashes.get(0);
            if (hash.equals(source.hash())) {
                return true;
            } else {
                throw new TransformationConflictException(
                        "Source at " + source + " does not match the stored hash " + hash);
            }
        } else {
            return false;
        }
    }

    @Override
    public List<Transformation> get(@NonNull TransformationKey key) {
        return jdbcTemplate.query(
                "SELECT from_version, to_version, transformation FROM transformationstore WHERE ns=? AND type=?",
                new Object[] { key.ns(), key.type() }, (rs, rowNum) -> {
                    int from = rs.getInt("from_version");
                    int to = rs.getInt("to_version");
                    String code = rs.getString("transformation");

                    return new SingleTransformation(key, from, to, Optional.ofNullable(code));
                });
    }
}