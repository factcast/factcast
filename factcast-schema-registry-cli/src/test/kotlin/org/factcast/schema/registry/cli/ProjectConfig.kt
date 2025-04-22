package org.factcast.schema.registry.cli

import io.kotest.core.NamedTag
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringExtension

object ProjectConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(SpringExtension)
}

val integration = NamedTag("integration")
