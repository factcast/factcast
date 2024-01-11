#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:1.9.0")


import io.github.typesafegithub.workflows.actions.actions.CheckoutV4
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV4
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
    sourceFile =  __FILE__.toPath(),
) {
    job(
        id = "formatting",
        runsOn = RunnerType.UbuntuLatest,
    ) {
        uses(
            name = "Checkout",
            action = CheckoutV4(
                token = "${'$'}{{ secrets.PAT }}",
            ),
        )
        uses(
            name = "JDK 17",
            action = SetupJavaV4(
                distribution = SetupJavaV4.Distribution.Corretto,
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
                actionVersion = "v5",
                inputs = mapOf(
                    "commit_message" to "Apply formatter",
                )
            ),
        )
    }

}

workflowFormat.writeToFile(addConsistencyCheck = false)
