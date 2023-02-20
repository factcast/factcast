#!/usr/bin/env kotlin

@file:DependsOn("it.krzeminski:github-actions-kotlin-dsl:0.37.0")

import it.krzeminski.githubactions.actions.CustomAction
import it.krzeminski.githubactions.actions.actions.CheckoutV3
import it.krzeminski.githubactions.actions.actions.SetupJavaV3
import it.krzeminski.githubactions.domain.RunnerType
import it.krzeminski.githubactions.domain.Workflow
import it.krzeminski.githubactions.domain.triggers.Push
import it.krzeminski.githubactions.dsl.workflow
import it.krzeminski.githubactions.yaml.writeToFile
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
            name = "CheckoutV3",
            action = CheckoutV3(
                token = "${'$'}{{ secrets.PAT }}",
            ),
        )
        uses(
            name = "SetupJavaV3",
            action = SetupJavaV3(
                distribution = SetupJavaV3.Distribution.Custom("corretto"),
                javaVersion = "11",
            ),
        )
        run(
            name = "Execute Spotless",
            command = "./mvnw -B spotless:apply --file pom.xml",
        )
        uses(
            name = "GitAutoCommitActionV4",
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
