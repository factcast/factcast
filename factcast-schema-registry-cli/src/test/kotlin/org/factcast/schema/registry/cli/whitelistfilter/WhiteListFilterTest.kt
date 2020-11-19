package org.factcast.schema.registry.cli.whitelistfilter

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.factcast.schema.registry.cli.project.structure.EventFolder
import org.factcast.schema.registry.cli.project.structure.EventVersionFolder
import org.factcast.schema.registry.cli.project.structure.NamespaceFolder
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
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

            val whiteList = listOf("shipping/OrderShipped/versions/1")
            val filteredProject = filterProject(project, whiteList)

            filteredProject.namespaces[0].eventFolders[0].versionFolders.size shouldBe 1
        }

        "oneEventIsFilteredOutSinceItIsNotInWhiteList" {
            val project = createProjectFolder(listOf(
                    createNamespaceFolder("shipping", listOf(
                            createEventFolder("shipping", "OrderShipped", listOf(
                                    createEventVersionFolder("shipping", "OrderShipped", 1),
                                    createEventVersionFolder("shipping", "OrderShipped", 2), // not whitelisted

                            ))
                    ))
            ))

            val whiteList = listOf("shipping/OrderShipped/versions/1")
            val filteredProject = filterProject(project, whiteList)

            filteredProject.namespaces[0].eventFolders[0].versionFolders.size shouldBe 1
            filteredProject.namespaces[0].eventFolders[0].versionFolders[0].path.endsWith("shipping/OrderShipped/versions/1") shouldBe true
        }


    }

    private fun filterProject(project: ProjectFolder, whiteList: List<String>, ): ProjectFolder {

        var namespaces = mutableListOf<NamespaceFolder>()
        project.copy().namespaces.forEach {nameSpaceFolder ->

            var eventFolders = mutableListOf<EventFolder>()
            nameSpaceFolder.eventFolders.forEach { eventFolder ->

                var eventVersionFolders = mutableListOf<EventVersionFolder>()
                eventFolder.versionFolders.forEach { eventVersionFolder ->
                    if (whiteList.any{whiteListEntry -> eventVersionFolder.path.endsWith(whiteListEntry)}) {
                        eventVersionFolders.add(eventVersionFolder)
                    }
                }
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