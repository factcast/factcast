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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.cache.GuavaCacheMetrics;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.StoreMetrics;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Fetches a SER from a Fact-Id.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public class PgFactIdToSerialMapper implements InitializingBean {

  private static final long MAX_SIZE = 10_000;
  private final JdbcTemplate jdbcTemplate;
  private final PgMetrics metrics;
  private MeterRegistry registry;

  private final Cache<UUID, Long> cache;

  public PgFactIdToSerialMapper(
      JdbcTemplate jdbcTemplate, PgMetrics metrics, MeterRegistry registry) {
    this.jdbcTemplate = jdbcTemplate;
    this.metrics = metrics;
    this.registry = registry;
    cache = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofHours(8)).softValues().build();
  }

  /**
   * Fetches the SER of a particular Fact identified by id
   *
   * @param id the FactId to look for
   * @return the corresponding SER, 0, if no Fact is found for the id given.
   */
  public long retrieve(UUID id) {
    if (id == null) {
      return 0;

    } else {
      Long cachedValue = cache.getIfPresent(id);
      if (cachedValue != null && cachedValue > 0) {
        return cachedValue;
      } else {
        return metrics.time(
            StoreMetrics.OP.SERIAL_OF,
            () -> {
              try {
                Long res =
                    jdbcTemplate.queryForObject(
                        PgConstants.SELECT_SER_BY_ID, new Object[] {id}, Long.class);
                if (res != null && res > 0) {
                  cache.put(id, res);
                  return res;
                }
              } catch (EmptyResultDataAccessException ignore) {
                // ignore
              }
              return 0L;
            });
      }
    }
  }

  // has been pulled out of constructor in order to allow easier unit testing
  @Override
  public void afterPropertiesSet() throws Exception {
    GuavaCacheMetrics.monitor(
        registry,
        cache,
        "serialLookupCache",
        Collections.singleton(Tag.of(StoreMetrics.TAG_STORE_KEY, StoreMetrics.TAG_STORE_VALUE)));
  }
}
