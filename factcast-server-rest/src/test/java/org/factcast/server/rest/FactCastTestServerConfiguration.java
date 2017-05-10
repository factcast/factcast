package org.factcast.server.rest;

import org.factcast.core.store.FactStore;
import org.factcast.store.inmem.InMemFactStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SuppressWarnings("deprecation")

public class FactCastTestServerConfiguration {

    private InMemFactStore inMemFactStore = new InMemFactStore();

    @Bean
    FactStore getFactStore() {
        return inMemFactStore;
    }
}