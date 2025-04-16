package org.factcast.schema.registry.cli.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class ObjectMapperConfig {
    @Bean
    open fun objectMapper() = ObjectMapper()
}
