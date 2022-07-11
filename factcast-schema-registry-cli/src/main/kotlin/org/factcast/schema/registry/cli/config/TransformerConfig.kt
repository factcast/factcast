package org.factcast.schema.registry.cli.config

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import org.factcast.script.engine.EngineFactory
import org.factcast.script.engine.graaljs.GraalJSEngineCache
import org.factcast.store.registry.transformation.chains.GraalJsTransformer
import org.factcast.store.registry.transformation.chains.Transformer

@Factory
class TransformerConfig {
    @Bean
    fun engineFactory(): EngineFactory = GraalJSEngineCache()

    @Bean
    fun transformer(engineFactory: EngineFactory): Transformer =
        GraalJsTransformer(engineFactory)
}
