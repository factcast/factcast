package org.factcast.schema.registry.cli.config

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import org.factcast.store.internal.script.JSEngineFactory
import org.factcast.store.internal.script.graaljs.GraalJSEngineFactory
import org.factcast.store.registry.transformation.chains.JsTransformer
import org.factcast.store.registry.transformation.chains.Transformer

@Factory
class TransformerConfig {
    @Bean
    fun engineFactory(): JSEngineFactory = GraalJSEngineFactory()

    @Bean
    fun transformer(engineFactory: JSEngineFactory): Transformer =
        JsTransformer(engineFactory)
}
