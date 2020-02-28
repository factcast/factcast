/*
 * Copyright © 2017-2020 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.schema.registry.cli.commands

import mu.KotlinLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.output.WaitingConsumer
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import java.nio.file.Path
import javax.inject.Singleton

interface HugoService {
    fun execute(hugo: Path)
}

private val logger = KotlinLogging.logger {}

@Singleton
class HugoServiceImpl : HugoService {
    override fun execute(hugo: Path) {
        val hugoFile = hugo.toFile()
        if (!hugoFile.exists()) hugoFile.mkdirs()

        val consumer = WaitingConsumer()
        logger.info { "\nStarting Hugo" }
        logger.info { "Input : ${hugoFile.absolutePath}" }
        logger.info { "Output: ${hugoFile.absolutePath}/public" }

        // TODO how to get an UID into it? (to avoid that 'cannot delete' issue?)
        HugoContainer()
                .withFileSystemBind(hugoFile.absolutePath, "/srv/hugo")
                .waitingFor(WaitAllStrategy(WaitAllStrategy.Mode.WITH_INDIVIDUAL_TIMEOUTS_ONLY))
                .withCommand("hugo")
                .withLogConsumer(consumer)
                .withPrivilegedMode(false)
                .start()

        // TODO not sure if that actually works
        consumer.waitUntilEnd()
        logger.info { "\nHugo finished!" }
    }
}

class WaitingStdOutConsumer : WaitingConsumer() {

    // TODO why is there no output!?
    override fun accept(frame: OutputFrame) {
        logger.info { frame.utf8String }
    }
}

class HugoContainer() : GenericContainer<HugoContainer>("yanqd0/hugo")