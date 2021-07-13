package org.factcast.schema.registry.cli.config

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import org.factcast.store.pgsql.registry.transformation.chains.NashornTransformer
import org.factcast.store.pgsql.registry.transformation.chains.Transformer

@Factory
class TransformerConfig {
    @Bean
    fun transformer(): Transformer = NashornTransformer()
}