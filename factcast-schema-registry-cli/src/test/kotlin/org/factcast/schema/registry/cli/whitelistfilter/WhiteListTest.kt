package org.factcast.schema.registry.cli.whitelistfilter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

class WhiteListTest : StringSpec() {

    init {
        "positive match" {
            val projectPath = Paths.get("/parent")
            val whiteListConfig = listOf("/foo/**")
            val whiteList = WhiteList(projectPath, whiteListConfig)

            whiteList.matches(Paths.get("/parent/foo/bar")) shouldBe true
        }

        "no match" {
            val projectPath = Paths.get("/parent")
            val whiteListConfig = listOf("/foo/**")

            val whiteList = WhiteList(projectPath, whiteListConfig)

            whiteList.matches(Paths.get("/something/else/bar")) shouldBe false
        }
    }
}
