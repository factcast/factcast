/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.schema.registry.cli.whitelistfilter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.factcast.schema.registry.cli.project.structure.EventFolder
import org.factcast.schema.registry.cli.project.structure.EventVersionFolder
import org.factcast.schema.registry.cli.project.structure.NamespaceFolder
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.project.structure.TransformationFolder
import java.nio.file.Paths

class WhiteListFilterServiceImplTest : StringSpec() {

    val uut = WhiteListFilterServiceImpl()

    init {
        "oneEventInWhiteList" {
            val project = createProjectFolder(
                createNamespaceFolder(
                    "shipping",
                    createEventFolder(
                        "shipping",
                        "OrderShipped",
                        createEventVersionFolder("shipping", "OrderShipped", 1)
                    )
                )
            )

            val whiteList = listOf("/shipping/OrderShipped/versions/1")
            val filteredProject = uut.filter(project, whiteList)

            filteredProject.namespaces[0].eventFolders[0].versionFolders.size shouldBe 1
        }

        "oneEventIsFilteredOutSinceItIsNotInWhiteList" {
            val project = createProjectFolder(
                createNamespaceFolder(
                    "shipping",
                    createEventFolder(
                        "shipping",
                        "OrderShipped",
                        createEventVersionFolder("shipping", "OrderShipped", 1),
                        createEventVersionFolder("shipping", "OrderShipped", 2) // not whitelisted
                    )
                )
            )

            val whiteList = listOf("/shipping/OrderShipped/versions/1")
            val filteredProject = uut.filter(project, whiteList)

            filteredProject.namespaces[0].eventFolders[0].versionFolders.size shouldBe 1
            filteredProject.namespaces[0].eventFolders[0].versionFolders[0].path.endsWith("shipping/OrderShipped/versions/1") shouldBe true
        }

        "whiteListInTwoDifferentContexts" {
            val project = createProjectFolder(
                createNamespaceFolder(
                    "ordering",
                    createEventFolder(
                        "ordering",
                        "OrderReceived",
                        createEventVersionFolder("ordering", "OrderReceived", 1),
                        createEventVersionFolder("ordering", "OrderReceived", 2)
                    ),
                    createEventFolder(
                        "ordering",
                        "OrderProcessed",
                        createEventVersionFolder("ordering", "OrderProcessed", 1),
                        createEventVersionFolder("ordering", "OrderProcessed", 2)
                    )
                ),
                createNamespaceFolder(
                    "shipping",
                    createEventFolder(
                        "shipping",
                        "OrderShipped",
                        createEventVersionFolder("shipping", "OrderShipped", 1),
                        createEventVersionFolder("shipping", "OrderShipped", 2)
                    )
                )
            )

            val whiteList = listOf(
                "/ordering/OrderReceived/versions/1",
                "/ordering/OrderProcessed/versions/1",
                "/shipping/OrderShipped/versions/1"
            )
            val filteredProject = uut.filter(project, whiteList)

            filteredProject.namespaces[0].eventFolders[0].versionFolders.size shouldBe 1
            filteredProject.namespaces[0].eventFolders[0].versionFolders[0].path.endsWith("ordering/OrderReceived/versions/1") shouldBe true

            filteredProject.namespaces[0].eventFolders[1].versionFolders.size shouldBe 1
            filteredProject.namespaces[0].eventFolders[1].versionFolders[0].path.endsWith("ordering/OrderProcessed/versions/1") shouldBe true

            filteredProject.namespaces[1].eventFolders[0].versionFolders.size shouldBe 1
            filteredProject.namespaces[1].eventFolders[0].versionFolders[0].path.endsWith("shipping/OrderShipped/versions/1") shouldBe true
        }

        "specialCaseIfNoWhiteListEntryMatchesTheEventIsRemovedCompletely" {
            val project = createProjectFolder(
                createNamespaceFolder(
                    "shipping",
                    createEventFolder(
                        "shipping",
                        "OrderReceived",
                        createEventVersionFolder("shipping", "OrderReceived", 1)
                    ),
                    createEventFolder(
                        "shipping",
                        "OrderShipped",
                        createEventVersionFolder("shipping", "OrderShipped", 1)
                    )
                )
            )

            val whiteList = listOf(
                "/shipping/OrderReceived/versions/1"
            )

            val filteredProject = uut.filter(project, whiteList)
            filteredProject.namespaces[0].eventFolders.size shouldBe 1
        }

        "whiteListSupportsWildCards" {
            val project = createProjectFolder(
                createNamespaceFolder(
                    "shipping",
                    createEventFolder(
                        "shipping",
                        "OrderShipped",
                        createEventVersionFolder("shipping", "OrderShipped", 1)
                    )
                ),
                createNamespaceFolder(
                    "ordering",
                    createEventFolder(
                        "ordering",
                        "OrderReceived",
                        createEventVersionFolder("ordering", "OrderReceived", 1)
                    ),
                    createEventFolder(
                        "ordering",
                        "OrderProcessed",
                        createEventVersionFolder("ordering", "OrderProcessed", 1)
                    ),
                    createEventFolder(
                        "ordering",
                        "OrderProcessed",
                        createEventVersionFolder("ordering", "OrderProcessed", 2)
                    )
                )
            )

            val whiteList = listOf(
                "/shipping/**",
                "/ordering/Order*/versions/1"
            )
            val filteredProject = uut.filter(project, whiteList)

            filteredProject.namespaces[0].eventFolders[0].versionFolders.size shouldBe 1
            filteredProject.namespaces[1].eventFolders.size shouldBe 2
            filteredProject.namespaces[1].eventFolders[0].versionFolders.size shouldBe 1
            filteredProject.namespaces[1].eventFolders[1].versionFolders.size shouldBe 1
            filteredProject.namespaces[1].eventFolders[1].versionFolders[0].path.endsWith("OrderProcessed/versions/1") shouldBe true
        }

        "nonMentionedNameSpacesAreFiltered" {
            val project = createProjectFolder(
                createNamespaceFolder(
                    "shipping",
                    createEventFolder(
                        "shipping",
                        "OrderShipped",
                        createEventVersionFolder("shipping", "OrderShipped", 1)
                    )
                ),
                createNamespaceFolder(
                    "ordering",
                    createEventFolder(
                        "ordering",
                        "OrderReceived",
                        createEventVersionFolder("ordering", "OrderReceived", 1)
                    )
                )
            )

            val whiteList = listOf(
                "/shipping/**"
            )
            val filteredProject = uut.filter(project, whiteList)

            filteredProject.namespaces.size shouldBe 1
            filteredProject.namespaces[0].path.endsWith("shipping") shouldBe true
        }

        "nonWhitelistedEventIsFullyFilteredOutIncludingTransformationFolder " {
            val project = createProjectFolder(
                createNamespaceFolder(
                    "shipping",
                    createEventFolder(
                        "shipping",
                        "OrderShipped",
                        createEventVersionFolder("shipping", "OrderShipped", 1),
                        createEventVersionFolder("shipping", "OrderShipped", 2),
                        transformationFolder = listOf(createTransformationFolder("shipping", "OrderShipped", "1-2"))
                    )
                )
            )

            val whiteList = listOf("/shipping/OrderShipped/versions/1")
            val filteredProject = uut.filter(project, whiteList)

            filteredProject.namespaces[0].eventFolders[0].versionFolders.size shouldBe 1
            filteredProject.namespaces[0].eventFolders[0].transformationFolders.size shouldBe 0
        }

        "whiteListedEventsPassTheFilterIncludingTheTransformationFolder" {
            val project = createProjectFolder(
                createNamespaceFolder(
                    "shipping",
                    createEventFolder(
                        "shipping",
                        "OrderShipped",
                        createEventVersionFolder("shipping", "OrderShipped", 1),
                        createEventVersionFolder("shipping", "OrderShipped", 2),
                        transformationFolder = listOf(createTransformationFolder("shipping", "OrderShipped", "1-2"))
                    )
                )
            )

            val whiteList = listOf(
                "/shipping/OrderShipped/versions/1",
                "/shipping/OrderShipped/versions/2"
            )
            val filteredProject = uut.filter(project, whiteList)

            filteredProject.namespaces[0].eventFolders[0].versionFolders.size shouldBe 2
            filteredProject.namespaces[0].eventFolders[0].transformationFolders.size shouldBe 1
        }
    }

    private fun createProjectFolder(vararg namespaceFolders: NamespaceFolder) =
        ProjectFolder(
            path = Paths.get("/registry"),
            description = Paths.get("/registry/index.md"),
            namespaces = namespaceFolders.asList()
        )

    private fun createNamespaceFolder(namespaceName: String, vararg eventFolders: EventFolder) =
        NamespaceFolder(
            path = Paths.get("/registry/$namespaceName"),
            description = Paths.get("/registry/$namespaceName/index.md"),
            eventFolders = eventFolders.asList()
        )

    private fun createEventFolder(
        namespaceName: String,
        eventName: String,
        vararg versionFolders: EventVersionFolder,
        transformationFolder: List<TransformationFolder> = emptyList()
    ) =
        EventFolder(
            path = Paths.get("/registry/$namespaceName/$eventName"),
            description = Paths.get("/registry/$namespaceName/$eventName/index.md"),
            transformationFolders = transformationFolder,
            versionFolders = versionFolders.asList()
        )

    private fun createEventVersionFolder(namespaceName: String, eventName: String, version: Int = 1) =
        EventVersionFolder(
            path = Paths.get("/registry/$namespaceName/$eventName/versions/$version"),
            schema = Paths.get("/registry/$namespaceName/$eventName/versions/$version/schema.json"),
            description = Paths.get("/registry/$namespaceName/$eventName/versions/$version/index.md"),
            examples = listOf(Paths.get("/registry/$namespaceName/$eventName/versions/$version/examples/simple.json"))
        )

    private fun createTransformationFolder(namespaceName: String, eventName: String, transformationName: String) =
        TransformationFolder(
            path = Paths.get("/registry/$namespaceName/$eventName/transformations/$transformationName"),
            transformation = null
        )
}
