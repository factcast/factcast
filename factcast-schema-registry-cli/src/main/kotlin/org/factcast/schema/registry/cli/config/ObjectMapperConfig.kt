package org.factcast.schema.registry.cli.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory

@Factory
class ObjectMapperConfig {
    @Bean
    fun objectMapper() = ObjectMapper()
}