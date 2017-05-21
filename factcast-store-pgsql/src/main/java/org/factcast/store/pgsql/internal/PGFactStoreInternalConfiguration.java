package org.factcast.store.pgsql.internal;

import java.util.concurrent.Executors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

}
