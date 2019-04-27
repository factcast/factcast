/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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

import java.util.*;
import java.util.stream.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.context.event.*;
import org.springframework.boot.context.properties.*;
import org.springframework.context.*;
import org.springframework.core.env.*;

import lombok.*;
import lombok.experimental.*;
import lombok.extern.slf4j.*;

@SuppressWarnings("DefaultAnnotationParam")
@ConfigurationProperties(
        prefix = "factcast.store.pgsql",
        ignoreInvalidFields = false,
        ignoreUnknownFields = false)
@Data
@Slf4j
@Accessors(fluent = false)
public class PgConfigurationProperties implements ApplicationListener<ApplicationReadyEvent> {
    private static final String LEGACY_PREFIX = "factcast.pg";

    @Autowired
    Environment env;

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
     * The factor to apply, when fetching/queuing Ids rather than Facts (assuming,
     * that needs just a fraction of Heap and is way fater to flush to the client)
     */
    int idOnlyFactor = 100;

    /**
     * Defines the Strategy used for Paging in the Catchup Phase.
     */
    CatchupStrategy catchupStrategy = CatchupStrategy.getDefault();

    /**
     * Fetch Size used when filling the Queue, defaults to 4 (25% of the queue-size)
     */
    int queueFetchRatio = 4;

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
        Map<String, Object> map = new HashMap();
        MutablePropertySources propertySources = ((AbstractEnvironment) env).getPropertySources();
        for (Iterator it = propertySources.iterator(); it.hasNext(); ) {
            PropertySource propertySource = (PropertySource) it.next();
            if (propertySource instanceof MapPropertySource) {
                Map<String, Object> source = ((MapPropertySource) propertySource).getSource();
                source.entrySet().forEach(e -> {
                    map.put(e.getKey(), propertySource.toString());
                });
            }
        }

        List<Map.Entry<String, Object>> legacyPrperties = map.entrySet()
                .stream()
                .filter(e -> e.getKey().startsWith(LEGACY_PREFIX))
                .collect(Collectors.toList());
        if (!legacyPrperties.isEmpty()) {
            log.error(
                    "There are legacy properties detected. Property namespace has been renamed from '"
                            + LEGACY_PREFIX + "' to 'factcast.store.pgsql'");
            legacyPrperties.forEach(p -> {
                log.error("Property {} found in {}", p.getKey(), p.getValue());
            });
            System.exit(1);
        }

    }
}
