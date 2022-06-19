#!/usr/bin/env kotlin

@file:DependsOn("it.krzeminski:github-actions-kotlin-dsl:0.19.0")

import it.krzeminski.githubactions.actions.actions.CacheV3
import it.krzeminski.githubactions.actions.actions.CheckoutV3
import it.krzeminski.githubactions.actions.actions.SetupJavaV3
import it.krzeminski.githubactions.actions.codecov.CodecovActionV3
import it.krzeminski.githubactions.actions.docker.LoginActionV2
import it.krzeminski.githubactions.domain.RunnerType
import it.krzeminski.githubactions.domain.Workflow
import it.krzeminski.githubactions.domain.triggers.PullRequest
import it.krzeminski.githubactions.domain.triggers.Push
import it.krzeminski.githubactions.dsl.workflow
import it.krzeminski.githubactions.yaml.writeToFile

public val workflowMaven: Workflow = workflow(
    name = "maven",
    on = listOf(
        PullRequest(
            branches = listOf("master"),
        ),
        Push(
            branches = listOf("master"),
        ),
    ),
    sourceFile = __FILE__.toPath()
) {
    job(
        id = "build",
        runsOn = RunnerType.UbuntuLatest,
    ) {
        uses(
            name = "Login to Docker Hub",
            action = LoginActionV2(
                username = "${'$'}{{ secrets.DOCKERHUB_USERNAME }}",
                password = "${'$'}{{ secrets.DOCKERHUB_TOKEN }}",
            ),
        )
        uses(
            name = "CheckoutV3",
            action = CheckoutV3(),
        )
        uses(
            name = "CacheV3",
            action = CacheV3(
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
            name = "CacheV3",
            action = CacheV3(
                path = listOf(
                    "/var/lib/docker/",
                ),
                key = "factcast-docker-cache-unversioned",
                restoreKeys = listOf(
                    "factcast-docker-cache-unversioned",
                ),
            ),
        )
        uses(
            name = "Set up JDK 11",
            action = SetupJavaV3(
                distribution = SetupJavaV3.Distribution.Custom("corretto"),
                javaVersion = "11",
            ),
        )
        run(
            name = "Build & Test with Maven",
            command = "./mvnw -T1.5C -B clean test --file pom.xml",
        )
        run(
            name = "Remove partial execution reports",
            command = "find -wholename \"**/target/jacoco-output\" -exec rm -rf {} +",
        )
        uses(
            name = "CodecovActionV3",
            action = CodecovActionV3(
                token = "${'$'}{{ secrets.CODECOV_TOKEN }}",
            ),
        )
        run(
            name = "Build & Verify with Maven",
            command = "./mvnw -B verify -DskipUnit=true --file pom.xml",
        )
    }

}

workflowMaven.writeToFile()
