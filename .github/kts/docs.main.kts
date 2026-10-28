#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:4.0.0")

@file:Repository("https://bindings.krzeminski.it")
@file:DependsOn("actions:checkout:v7")
@file:DependsOn("actions:setup-java:v6")
@file:DependsOn("actions:configure-pages:v6")
@file:DependsOn("actions:upload-pages-artifact:v5")
@file:DependsOn("actions:deploy-pages:v5")

import io.github.typesafegithub.workflows.actions.actions.*
import io.github.typesafegithub.workflows.domain.Environment
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.domain.triggers.Release
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Publish Maven Site",
    on = listOf(
        Release(types = listOf(Release.EventType.Published)),
        Push(
            branches = listOf("main"),
            paths = listOf("factcast-docs/src/main/asciidoc/modules/blog/pages/**"),
        ),
    ),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    permissions = mapOf(
        Permission.Contents to Mode.Read,
        Permission.Pages to Mode.Write,
        Permission.IdToken to Mode.Write,
        Permission.Actions to Mode.Read,
    ),
) {
    job(
        id = "deploy",
        runsOn = UbuntuLatest,
        condition = expr("github.event_name == 'push' || !github.event.release.prerelease"),
        environment = Environment(name = "github-pages"),
    ) {
        uses(
            name = "Checkout documentation source",
            action = Checkout(
                ref = expr("github.event.release.tag_name || github.sha"),
            ),
        )

        uses(
            name = "Set up Java",
            action = SetupJava(
                distribution = SetupJava.Distribution.Temurin,
                javaVersion = "25",
            ),
        )

        run(
            name = "Generate Docs",
            command = "./mvnw -B -pl factcast-docs clean package",
        )

        uses(
            name = "Configure Pages",
            action = ConfigurePages(),
        )

        uses(
            name = "Upload Maven site",
            action = UploadPagesArtifact(
                path = "factcast-docs/target/site",
            ),
        )

        uses(
            name = "Deploy Pages",
            action = DeployPages(),
        )
    }
}
