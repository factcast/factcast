#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.6.0")

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:Repository("https://bindings.krzeminski.it")

@file:DependsOn("actions:checkout:v6")
@file:DependsOn("actions:cache:v4")
@file:DependsOn("actions:setup-java:v5")

import io.github.typesafegithub.workflows.actions.actions.Cache
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.actions.CustomAction
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Maven UITest",
    on = listOf(
        PullRequest(),
        Push(
            branches = listOf("main"),
        ),
    ),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled

) {
    job(
        id = "build",
        runsOn = RunnerType.UbuntuLatest,
    ) {
        uses(
            name = "Checkout",
            action = Checkout(fetchDepth = Checkout.FetchDepth.Infinite),
        )
        uses(
            name = "Cache - Maven Repository",
            action = Cache(
                path = listOf(
                    "~/.m2/repository",
                ),
                key = "${'$'}{{ runner.os }}-maven-${'$'}{{ hashFiles('**/pom.xml') }}",
                restoreKeys = listOf(
                    "${'$'}{{ runner.os }}-maven-",
                ),
            ),
        )
        uses(
            name = "JDK 21",
            action = SetupJava(
                distribution = SetupJava.Distribution.Corretto,
                javaVersion = "21",
            ),
        )

        run(
            name = "Build with Maven, no testing",
            command = "./mvnw -B clean install -DskipTests",
        )

        run(
            name = "Test - UI",
            command = "cd factcast-server-ui ; ../mvnw -B -Dui verify",
        )

        uses(
            name = "Commit vaadin changes",
            action = CustomAction(
                actionOwner = "stefanzweifel",
                actionName = "git-auto-commit-action",
                actionVersion = "778341af668090896ca464160c2def5d1d1a3eb0", // v6.0.1
                inputs = mapOf(
                    "commit_message" to "Update vaadin files",
                    "file_pattern" to "factcast-server-ui/src/main/bundles/prod.bundle factcast-server-ui/package.json"
                )
            ),
        )
    }
}
