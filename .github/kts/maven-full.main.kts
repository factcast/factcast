#!/usr/bin/env kotlin

@file:DependsOn("it.krzeminski:github-actions-kotlin-dsl:0.27.0")

import it.krzeminski.githubactions.actions.actions.CacheV3
import it.krzeminski.githubactions.actions.actions.CheckoutV3
import it.krzeminski.githubactions.actions.actions.SetupJavaV3
import it.krzeminski.githubactions.actions.docker.LoginActionV2
import it.krzeminski.githubactions.domain.RunnerType
import it.krzeminski.githubactions.domain.Workflow
import it.krzeminski.githubactions.domain.triggers.PullRequest
import it.krzeminski.githubactions.domain.triggers.Push
import it.krzeminski.githubactions.dsl.workflow
import it.krzeminski.githubactions.yaml.writeToFile
import java.nio.file.Paths

public val workflowMaven: Workflow = workflow(
    name = "Maven Full profile",
    on = listOf(
        PullRequest(
            branches = listOf("master"),
        ),
        Push(
            branches = listOf("master"),
        ),
    ),
    sourceFile = Paths.get(".github/kts/maven-full.main.kts"),
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
            action = CheckoutV3(fetchDepth = CheckoutV3.FetchDepth.Infinite)
        )
        uses(
            name = "CacheV3 - m2",
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
            name = "CacheV3 - sonar",
            action = CacheV3(
                path = listOf(
                    "~/.sonar/cache",
                ),
                key = "${'$'}{{ runner.os }}-sonar",
                restoreKeys = listOf(
                    "${'$'}{{ runner.os }}-sonar",
                ),
            ),
        )
        uses(
            name = "CacheV3 - docker",
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
            name = "Build with Maven - test, verify and analyze",
            command = "./mvnw -B clean install --file pom.xml",
        )
        run(
            name = "Run sonar upload",
            command = "./mvnw -N -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectKey=factcast -Dsonar.organization=factcast -Dsonar.host.url=https://sonarcloud.io -Dsonar.login=\${{ secrets.SONAR_TOKEN }} -Dsonar --file pom.xml",
        )
    }

}

workflowMaven.writeToFile(addConsistencyCheck = false)
