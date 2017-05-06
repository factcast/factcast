package org.factcast.store.inmem;

import org.factcast.core.store.FactStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration to include in order to use a PGFactStore
 * 
 * just forwards to {@link PGFactStoreInternalConfiguration}, so that IDEs can
 * still complain about internal references.
 * 
 * @author uwe.schaefer@mercateo.com, joerg.adler@mercateo.com
 *
 */
@Configuration
public class InMemFactStoreConfiguration {
    @SuppressWarnings("deprecation")
    @Bean
    @ConditionalOnMissingBean(FactStore.class)
    public FactStore factStore() {
        return new InMemFactStore();
    }
}
