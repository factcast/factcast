#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:0.42.0")


import io.github.typesafegithub.workflows.actions.actions.CacheV3
import io.github.typesafegithub.workflows.actions.actions.CheckoutV3
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV3
import io.github.typesafegithub.workflows.actions.codecov.CodecovActionV3
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.Workflow
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.writeToFile
import java.nio.file.Paths

public val workflowMaven: Workflow = workflow(
    name = "Maven Quick profile",
    on = listOf(
        PullRequest(
            branches = listOf("master"),
        ),
        Push(
            branches = listOf("master"),
        ),
    ),
    sourceFile = Paths.get(".github/kts/maven-quick.main.kts"),
) {
    job(
        id = "build",
        runsOn = RunnerType.UbuntuLatest,
    ) {
        uses(
            name = "CheckoutV3",
            action = CheckoutV3(fetchDepth = CheckoutV3.FetchDepth.Infinite)
        )
        uses(
            name = "CacheV3 - maven repository",
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
            name = "Build with Maven",
            command = "./mvnw -B clean test --file pom.xml",
        )

        run(
            name = "Run sonar upload",
            command = "./mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectKey=factcast -Dsonar.organization=factcast -Dsonar.host.url=https://sonarcloud.io -Dsonar.login=\${{ secrets.SONAR_TOKEN }} -Pcoverage --file pom.xml"
        )

        run(
            name = "Build with Maven - test, verify and analyze",
            command = "./mvnw -B install -DskipUnitTests --file pom.xml",
        )
        uses(
            name = "CodecovActionV3",
            action = CodecovActionV3(
                token = "${'$'}{{ secrets.CODECOV_TOKEN }}",
                files = listOf("target/jacoco-merged.exec"),
                flags = listOf("unittests"),
            ),
        )
    }
}


workflowMaven.writeToFile(addConsistencyCheck = false)

