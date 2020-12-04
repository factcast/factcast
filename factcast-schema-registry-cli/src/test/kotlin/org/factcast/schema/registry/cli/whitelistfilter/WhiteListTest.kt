package org.factcast.schema.registry.cli.whitelistfilter

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
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
