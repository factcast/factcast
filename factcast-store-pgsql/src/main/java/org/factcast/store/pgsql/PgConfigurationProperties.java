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
package org.factcast.store.pgsql;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("DefaultAnnotationParam")
@ConfigurationProperties(prefix = PgConfigurationProperties.PROPERTIES_PREFIX)
@Data
@Slf4j
@Accessors(fluent = false)
public class PgConfigurationProperties implements ApplicationListener<ApplicationReadyEvent> {
    private static final String LEGACY_PREFIX = "factcast.pg";

    public static final String PROPERTIES_PREFIX = "factcast.store.pgsql";

    @Autowired
    Environment env;

    @Autowired
    private ApplicationContext appContext;

    /**
     * defines the number of Facts being retrieved with one Page Query for
     * PageStrategy.PAGED
     */
    int pageSize = 1000;

    /**
     * The capacity of the queue for PageStrategy.QUEUED
     */
    int queueSize = 1000;

    /**
     * The factor to apply, when fetching/queuing Ids rather than Facts
     * (assuming, that needs just a fraction of Heap and is way faster to flush
     * to the client)
     */
    int idOnlyFactor = 100;

    /**
     * Defines the Strategy used for Paging in the Catchup Phase.
     */
    CatchupStrategy catchupStrategy = CatchupStrategy.getDefault();

    /**
     * Fetch Size used when filling the Queue, defaults to 4 (25% of the
     * queue-size)
     */
    int queueFetchRatio = 4;

    /**
     * Optional URL to a Schema Registry. If this is null, validation will be
     * disabled and a warning will be issued. (Defaults to null) Currently a
     * String type due to the fact that "classpath:" is a spring-only protocol
     */
    String schemaRegistryUrl;

    /**
     * If validation is enabled, this controls if the local snapshot of the
     * registry is persisted to psql or just kept in mem. (Defaults to true)
     */
    boolean persistentRegistry = true;

    /**
     * when using the persistent impl of the transformation cache, this is the
     * min number of days a transformation result is not read in order to be
     * considered stale. This should free some space in a regular cleanup job
     */
    int deleteTransformationsStaleForDays = 14;

    /**
     * If validation is enabled, this controls if transformed facts are
     * persistently cached in postgres, rather than in memory. (Defaults to
     * false)
     */
    boolean persistentTransformationCache = false;

    /**
     * when using the inmem impl of the transformation cache, this is the max
     * number of entries cached.
     */
    int inMemTransformationCacheCapacity = 1_000_000;

    /**
     * If validation is enabled, this controls if publishing facts, that are not
     * validatable (due to missing meta-data or due to missing schema in the
     * registry) are allowed to be published or should be rejected. (Defaults to
     * false)
     */
    boolean allowUnvalidatedPublish = false;

    /**
     * if validation is enabled, this defines the rate (in milliseconds) in
     * which the local store is refreshed. (Defaults to 15000)
     */
    long schemaStoreRefreshRateInMilliseconds = 15000;

    public int getPageSizeForIds() {
        return pageSize * idOnlyFactor;
    }

    public int getQueueSizeForIds() {
        return queueSize * idOnlyFactor;
    }

    public int getFetchSizeForIds() {
        return getQueueSizeForIds() / queueFetchRatio;
    }

    public int getFetchSize() {
        return getQueueSize() / queueFetchRatio;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        List<Map.Entry<String, Object>> legacyProperties = findAllProperties().entrySet()
                .stream()
                .filter(e -> e.getKey().startsWith(LEGACY_PREFIX))
                .collect(Collectors.toList());

        if (!legacyProperties.isEmpty()) {
            log.error(
                    "There are legacy properties detected. Property namespace has been renamed from '"
                            + LEGACY_PREFIX
                            + "' to 'factcast.store.pgsql'");
            legacyProperties.forEach(p -> {
                log.error("Property {} found in {}", p.getKey(), p.getValue());
            });
        }
    }

    private Map<String, Object> findAllProperties() {
        Map<String, Object> map = new HashMap<>();
        MutablePropertySources propertySources = ((AbstractEnvironment) env).getPropertySources();
        for (Iterator<?> it = propertySources.iterator(); it.hasNext();) {
            PropertySource<?> propertySource = (PropertySource<?>) it.next();
            if (propertySource instanceof MapPropertySource) {
                Map<String, Object> source = ((MapPropertySource) propertySource).getSource();
                source.entrySet().forEach(e -> {
                    map.put(e.getKey(), propertySource.toString());
                });
            }
        }
        return map;
    }

    public boolean isValidationEnabled() {
        return schemaRegistryUrl != null;
    }
}
