package org.factcast.schema.registry.cli.config

import org.factcast.store.internal.script.JSEngineFactory
import org.factcast.store.internal.script.graaljs.GraalJSEngineFactory
import org.factcast.store.registry.transformation.chains.JsTransformer
import org.factcast.store.registry.transformation.chains.Transformer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TransformerConfig {
    @Bean
    fun engineFactory(): JSEngineFactory = GraalJSEngineFactory()

    @Bean
    fun transformer(engineFactory: JSEngineFactory): Transformer =
        JsTransformer(engineFactory)
}
