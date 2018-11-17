/**
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
package org.factcast.store.pgsql.internal;

import java.util.concurrent.Executors;

import org.factcast.store.pgsql.PGConfigurationProperties;
import org.factcast.store.pgsql.internal.catchup.PGCatchupFactory;
import org.factcast.store.pgsql.internal.catchup.paged.PGPagedCatchUpFactory;
import org.factcast.store.pgsql.internal.catchup.queue.PGQueueCatchUpFactory;
import org.factcast.store.pgsql.internal.query.PGFactIdToSerialMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;

/**
 * Main @Configuration class for a PGFactStore
 *
 * @author uwe.schaefer@mercateo.com
 */
@Configuration
public class PGFactStoreInternalConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventBus.class)
    public EventBus eventBus() {
        return new AsyncEventBus(this.getClass().getSimpleName(), Executors.newCachedThreadPool());
    }

    @Bean
    public PGCatchupFactory pgCatchupFactory(PGConfigurationProperties props, JdbcTemplate jdbc,
            PGFactIdToSerialMapper serMapper) {
        switch (props.getCatchupStrategy()) {
        case PAGED:
            return new PGPagedCatchUpFactory(jdbc, props, serMapper);
        case QUEUED:
            return new PGQueueCatchUpFactory(jdbc, props, serMapper);
        default:
            throw new IllegalArgumentException("Unmapped Strategy: " + props.getCatchupStrategy());
        }
    }
}
