#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")


@file:Repository("https://repo.maven.apache.org/maven2/")
@file:Repository("https://bindings.krzeminski.it")

@file:DependsOn("actions:checkout:v6")
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
            name = "JDK 17",
            action = SetupJava(
                distribution = SetupJava.Distribution.Corretto,
                javaVersion = "17",
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
                actionVersion = "778341af668090896ca464160c2def5d1d1a3eb0", // v6.0.1
                inputs = mapOf(
                    "commit_message" to "Apply formatter",
                )
            ),
        )
    }
}
