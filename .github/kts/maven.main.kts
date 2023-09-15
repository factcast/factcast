#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:1.1.0")


import io.github.typesafegithub.workflows.actions.actions.CacheV3
import io.github.typesafegithub.workflows.actions.actions.CheckoutV3
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV3
import io.github.typesafegithub.workflows.actions.codecov.CodecovActionV3
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.Workflow
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.writeToFile
import java.nio.file.Paths

public val workflowMaven: Workflow = workflow(
    name = "Maven all in one",
    on = listOf(
        PullRequest(
            branches = listOf("master"),
        ),
        Push(
            branches = listOf("master"),
        ),
    ),
    sourceFile = Paths.get(".github/kts/maven.main.kts"),
) {
    job(
        id = "build",
        runsOn = RunnerType.UbuntuLatest,
    ) {
        uses(
            name = "Checkout",
            action = CheckoutV3(fetchDepth = CheckoutV3.FetchDepth.Infinite)
        )
        uses(
            name = "Cache - Maven Repository",
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
            name = "Cache - Sonar cache",
            action = CacheV3(
                path = listOf(
                    "~/.sonar/cache",
                ),
                key = "${'$'}{{ runner.os }}-sonar-${'$'}{{ hashFiles('**/pom.xml') }}",
                restoreKeys = listOf(
                    "${'$'}{{ runner.os }}-sonar-",
                ),
            ),
        )
        uses(
            name = "JDK 11",
            action = SetupJavaV3(
                distribution = SetupJavaV3.Distribution.Custom("corretto"),
                javaVersion = "11",
            ),
        )

        run(
            name = "Build with Maven, no testing",
            command = "./mvnw -B clean install -DskipTests",
        )

        run(
            name = "Test - Unit",
            command = "./mvnw -B test",
        )

        run(
            name = "Sonar upload",
            command = "./mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectKey=factcast -Dsonar.organization=factcast -Dsonar.host.url=https://sonarcloud.io -Dsonar.login=\${{ secrets.SONAR_TOKEN }}"
        )

        run(
            name = "Test - Integration",
            command = "./mvnw -B verify -DskipUnitTests",
        )
        uses(
            name = "Codecov upload",
            action = CodecovActionV3(
                token = "${'$'}{{ secrets.CODECOV_TOKEN }}"
            ),
        )
    }

    job(
        id = "postgres-compatibility",
        runsOn = RunnerType.UbuntuLatest,
        strategyMatrix = mapOf(
            "postgresVersion" to listOf("11", "12", "13", "14", "15"),
        ),
    ) {
        uses(
            name = "Checkout",
            action = CheckoutV3(fetchDepth = CheckoutV3.FetchDepth.Infinite)
        )
        uses(
            name = "Cache - Maven Repository",
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
            name = "JDK 11",
            action = SetupJavaV3(
                distribution = SetupJavaV3.Distribution.Custom("corretto"),
                javaVersion = "11",
            ),
        )

        run(
            name = "Test - Integration",
            command = "./mvnw -B -Dpostgres.version=${expr("matrix.postgresVersion")} verify -DskipUnitTests",
        )
    }
}


workflowMaven.writeToFile(addConsistencyCheck = false)

