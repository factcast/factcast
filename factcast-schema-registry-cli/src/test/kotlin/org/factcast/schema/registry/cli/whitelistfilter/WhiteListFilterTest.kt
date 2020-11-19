package org.factcast.schema.registry.cli.whitelistfilter

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.factcast.schema.registry.cli.project.structure.EventFolder
import org.factcast.schema.registry.cli.project.structure.EventVersionFolder
import org.factcast.schema.registry.cli.project.structure.NamespaceFolder
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import java.nio.file.FileSystems
import java.nio.file.Paths

class WhiteListFilterTest : StringSpec() {

    init {
        "oneEventInWhiteList" {
            val project = createProjectFolder(listOf(
                    createNamespaceFolder("shipping", listOf(
                            createEventFolder("shipping", "OrderShipped", listOf(
                                    createEventVersionFolder("shipping", "OrderShipped", 1)
                            ))
                    ))
            ))

            val whiteList = listOf("/shipping/OrderShipped/versions/1")
            val filteredProject = filterProject(project, whiteList)

            filteredProject.namespaces[0].eventFolders[0].versionFolders.size shouldBe 1
        }

        "oneEventIsFilteredOutSinceItIsNotInWhiteList" {
            val project = createProjectFolder(listOf(
                    createNamespaceFolder("shipping", listOf(
                            createEventFolder("shipping", "OrderShipped", listOf(
                                    createEventVersionFolder("shipping", "OrderShipped", 1),
                                    createEventVersionFolder("shipping", "OrderShipped", 2) // not whitelisted
                            ))
                    ))
            ))

            val whiteList = listOf("/shipping/OrderShipped/versions/1")
            val filteredProject = filterProject(project, whiteList)

            filteredProject.namespaces[0].eventFolders[0].versionFolders.size shouldBe 1
            filteredProject.namespaces[0].eventFolders[0].versionFolders[0].path.endsWith("shipping/OrderShipped/versions/1") shouldBe true
        }

        "whiteListInTwoDifferentContexts" {
            val project = createProjectFolder(listOf(
                    createNamespaceFolder("ordering", listOf(
                            createEventFolder("ordering", "OrderReceived", listOf(
                                    createEventVersionFolder("ordering", "OrderReceived", 1),
                                    createEventVersionFolder("ordering", "OrderReceived", 2)
                            )),
                            createEventFolder("ordering", "OrderProcessed", listOf(
                                    createEventVersionFolder("ordering", "OrderProcessed", 1),
                                    createEventVersionFolder("ordering", "OrderProcessed", 2)
                            ))
                    )),
                    createNamespaceFolder("shipping", listOf(
                            createEventFolder("shipping", "OrderShipped", listOf(
                                    createEventVersionFolder("shipping", "OrderShipped", 1),
                                    createEventVersionFolder("shipping", "OrderShipped", 2)
                            ))
                    ))
            ))

            val whiteList = listOf(
                    "/ordering/OrderReceived/versions/1",
                    "/ordering/OrderProcessed/versions/1",
                    "/shipping/OrderShipped/versions/1"
            )
            val filteredProject = filterProject(project, whiteList)

            filteredProject.namespaces[0].eventFolders[0].versionFolders.size shouldBe 1
            filteredProject.namespaces[0].eventFolders[0].versionFolders[0].path.endsWith("ordering/OrderReceived/versions/1") shouldBe true

            filteredProject.namespaces[0].eventFolders[1].versionFolders.size shouldBe 1
            filteredProject.namespaces[0].eventFolders[1].versionFolders[0].path.endsWith("ordering/OrderProcessed/versions/1") shouldBe true

            filteredProject.namespaces[1].eventFolders[0].versionFolders.size shouldBe 1
            filteredProject.namespaces[1].eventFolders[0].versionFolders[0].path.endsWith("shipping/OrderShipped/versions/1") shouldBe true
        }

        "specialCaseIfNoWhiteListEntryMatchesTheEventIsRemovedCompletely" {
            val project = createProjectFolder(listOf(
                    createNamespaceFolder("shipping", listOf(
                            createEventFolder("shipping", "OrderShipped", listOf(
                                    createEventVersionFolder("shipping", "OrderShipped", 1)
                            ))
                    ))
            ))

            val filteredProject = filterProject(project, emptyList())
            filteredProject.namespaces[0].eventFolders.size shouldBe 0
        }

        "whiteListSupportsWildCards" {
            val project = createProjectFolder(listOf(
                    createNamespaceFolder("shipping", listOf(
                            createEventFolder("shipping", "OrderShipped", listOf(
                                    createEventVersionFolder("shipping", "OrderShipped", 1)
                            ))
                    )),
                    createNamespaceFolder("ordering", listOf(
                            createEventFolder("ordering", "OrderReceived", listOf(
                                    createEventVersionFolder("ordering", "OrderReceived", 1),
                            )),
                            createEventFolder("ordering", "OrderProcessed", listOf(
                                    createEventVersionFolder("ordering", "OrderProcessed", 1),
                            )),
                            createEventFolder("ordering", "OrderProcessed", listOf(
                                    createEventVersionFolder("ordering", "OrderProcessed", 2),
                            ))
                    )),
            ))

            val whiteList = listOf(
                    "/shipping/**",
                    "/ordering/Order*/versions/1"
            )
            val filteredProject = filterProject(project, whiteList)

            filteredProject.namespaces[0].eventFolders[0].versionFolders.size shouldBe 1
            filteredProject.namespaces[1].eventFolders.size shouldBe 2
            filteredProject.namespaces[1].eventFolders[0].versionFolders.size shouldBe 1
            filteredProject.namespaces[1].eventFolders[1].versionFolders.size shouldBe 1
            filteredProject.namespaces[1].eventFolders[1].versionFolders[0].path.endsWith("OrderProcessed/versions/1") shouldBe true
        }
    }

    // returns a filtered ProjectFolder.
    // the original structure is iterated, filtered and freshly rebuild from inside to outside
    private fun filterProject(project: ProjectFolder, whiteList: List<String>): ProjectFolder {
        val whiteListPathMatchers = whiteList
                .map { "glob:${project.path}${it}" }
                .map { FileSystems.getDefault().getPathMatcher(it) }

        var namespaces = mutableListOf<NamespaceFolder>()
        project.copy().namespaces.forEach { nameSpaceFolder ->

            var eventFolders = mutableListOf<EventFolder>()
            nameSpaceFolder.eventFolders.forEach { eventFolder ->

                var eventVersionFolders = mutableListOf<EventVersionFolder>()
                eventFolder.versionFolders.forEach { eventVersionFolder ->
                    if (whiteListPathMatchers.any { pathMatcher -> pathMatcher.matches(eventVersionFolder.path) }) {
                        eventVersionFolders.add(eventVersionFolder)
                    }
                }

                if (eventVersionFolders.isEmpty()) return@forEach // if there is no version ignore the event completely
                eventFolders.add(EventFolder(
                        eventFolder.path, eventVersionFolders, eventFolder.description, eventFolder.transformationFolders))
            }
            namespaces.add(NamespaceFolder(nameSpaceFolder.path, eventFolders, nameSpaceFolder.description))
        }

        return ProjectFolder(project.path, project.description, namespaces)
    }

    private fun createProjectFolder(namespaceFolders: List<NamespaceFolder>) =
            ProjectFolder(
                    path = Paths.get("/registry"),
                    description = Paths.get("/registry/index.md"),
                    namespaces = namespaceFolders)

    private fun createNamespaceFolder(namespaceName: String, eventFolders: List<EventFolder>) =
            NamespaceFolder(
                    path = Paths.get("/registry/${namespaceName}"),
                    description = Paths.get("/registry/${namespaceName}/index.md"),
                    eventFolders = eventFolders)

    private fun createEventFolder(namespaceName: String, eventName: String, versionFolders: List<EventVersionFolder>) =
            EventFolder(
                    path = Paths.get("/registry/${namespaceName}/${eventName}"),
                    description = Paths.get("/registry/${namespaceName}/${eventName}/index.md"),
                    transformationFolders = emptyList(),
                    versionFolders = versionFolders)

    private fun createEventVersionFolder(namespaceName: String, eventName: String, version: Int = 1) =
            EventVersionFolder(
                    path = Paths.get("/registry/${namespaceName}/${eventName}/versions/${version}"),
                    schema = Paths.get("/registry/${namespaceName}/${eventName}/versions/${version}/schema.json"),
                    description = Paths.get("/registry/${namespaceName}/${eventName}/versions/${version}/index.md"),
                    examples = listOf(Paths.get("/registry/${namespaceName}/${eventName}/versions/${version}/examples/simple.json")))
}