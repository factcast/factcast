#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:0.50.0")


import io.github.typesafegithub.workflows.actions.actions.CheckoutV3
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV3
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.Workflow
import io.github.typesafegithub.workflows.domain.actions.CustomAction
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.writeToFile
import java.nio.file.Paths

public val workflowFormat: Workflow = workflow(
    name = "Format",
    on = listOf(Push()),
    sourceFile = Paths.get(".github/kts/format.main.kts"),
) {
    job(
        id = "formatting",
        runsOn = RunnerType.UbuntuLatest,
    ) {
        uses(
            name = "Checkout",
            action = CheckoutV3(
                token = "${'$'}{{ secrets.PAT }}",
            ),
        )
        uses(
            name = "JDK 17",
            action = SetupJavaV3(
                distribution = SetupJavaV3.Distribution.Custom("corretto"),
                javaVersion = "17",
            ),
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
                actionVersion = "v4",
                inputs = mapOf(
                    "commit_message" to "Apply formatter",
                )
            ),
        )
    }

}

workflowFormat.writeToFile(addConsistencyCheck = false)
