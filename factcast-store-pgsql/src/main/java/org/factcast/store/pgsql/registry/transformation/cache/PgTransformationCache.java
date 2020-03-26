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
package org.factcast.store.pgsql.registry.transformation.cache;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.store.pgsql.registry.metrics.MetricEvent;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.TimedOperation;
import org.joda.time.DateTime;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PgTransformationCache implements TransformationCache {
    private final JdbcTemplate jdbcTemplate;

    private final RegistryMetrics registryMetrics;

    @Override
    public void put(@NonNull Fact fact, @NonNull String transformationChainId) {
        String cacheKey = CacheKey.of(fact, transformationChainId);

        jdbcTemplate.update(
                "INSERT INTO transformationcache (cache_key, header, payload) VALUES (?, ?, ?)",
                cacheKey, fact.jsonHeader(), fact.jsonPayload());
    }

    @Override
    public Optional<Fact> find(@NonNull UUID eventId, int version,
            @NonNull String transformationChainId) {
        String cacheKey = CacheKey.of(eventId, version, transformationChainId);

        List<Fact> facts = jdbcTemplate.query(
                "SELECT header, payload FROM transformationcache WHERE cache_key = ?",
                new Object[] {
                        cacheKey }, ((rs, rowNum) -> {
                            String header = rs.getString("header");
                            String payload = rs.getString("payload");

                            return Fact.of(header, payload);
                        }));

        if (facts.isEmpty()) {
            registryMetrics.count(MetricEvent.TRANSFORMATION_CACHE_MISS);

            return Optional.empty();
        }

        jdbcTemplate.update("UPDATE transformationcache SET last_access=now() WHERE cache_key = ?",
                cacheKey);

        registryMetrics.count(MetricEvent.TRANSFORMATION_CACHE_HIT);

        return Optional.of(facts.get(0));
    }

    @Override
    public void compact(@NonNull DateTime thresholdDate) {
        registryMetrics.timed(TimedOperation.COMPACT_TRANSFORMATION_CACHE, () -> {
            jdbcTemplate.update("DELETE FROM transformationcache WHERE last_access < ?",
                    thresholdDate
                            .toDate());
        });

    }
}
