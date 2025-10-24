#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.5.0")


@file:Repository("https://repo.maven.apache.org/maven2/")
@file:Repository("https://bindings.krzeminski.it")

@file:DependsOn("actions:checkout:v5")
@file:DependsOn("actions:setup-java:v5")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.actions.CustomAction
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Format",
    on = listOf(Push()),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled
) {
    job(
        id = "formatting",
        runsOn = RunnerType.UbuntuLatest,
    ) {
        uses(
            name = "Checkout",
            action = Checkout(
                token = "${'$'}{{ secrets.PAT }}",
            ),
        )
        uses(
            name = "JDK 25",
            action = SetupJava(
                distribution = SetupJava.Distribution.Corretto,
                javaVersion = "25",
            ),
        )
        run(
            name = "Spotless",
            command = "./mvnw -B sortpom:sort --file pom.xml",
        )
        run(
            name = "Spotless",
            command = "./mvnw -B --non-recursive spotless:apply --file pom.xml",
        )
        uses(
            name = "Commit formatting changes",
            action = CustomAction(
                actionOwner = "stefanzweifel",
                actionName = "git-auto-commit-action",
                actionVersion = "v6",
                inputs = mapOf(
                    "commit_message" to "Apply formatter",
                )
            ),
        )
    }
}
