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
 *
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
