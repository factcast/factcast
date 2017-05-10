package org.factcast.server.rest;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnMissingBean(ResourceConfig.class)
public class FactCastRestConfig {

    @Bean
    public FactCastRestApplication factCastRestApplication() {
        return new FactCastRestApplication();
    }

}
