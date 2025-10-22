#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.5.0")


@file:Repository("https://repo.maven.apache.org/maven2/")
@file:Repository("https://bindings.krzeminski.it")

@file:DependsOn("actions:checkout:v5")
@file:DependsOn("actions:cache:v4")
@file:DependsOn("actions:setup-java:v5")


import io.github.typesafegithub.workflows.actions.actions.Cache
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.expressions.Contexts
import io.github.typesafegithub.workflows.dsl.expressions.Contexts.secrets
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig


workflow(
    name = "Maven all in one",
    on = listOf(
        PullRequest(),
        Push(
            branches = listOf("main"),
        ),
    ),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled
) {

    val SONAR_TOKEN by Contexts.secrets
    val SONAR  by Contexts.env

    job(
        id = "build",
        runsOn = RunnerType.UbuntuLatest,
    ) {
        uses(
            name = "Checkout",
            action = Checkout(fetchDepth = Checkout.FetchDepth.Infinite)
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
            name = "Cache - Sonar cache",
            action = Cache(
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
            name = "JDK 25",
            action = SetupJava(
                distribution = SetupJava.Distribution.Corretto,
                javaVersion = "25",
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
            env = mapOf( "SONAR" to expr { SONAR_TOKEN } ,),
            command = "./mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectKey=factcast -Dsonar.organization=factcast -Dsonar.host.url=https://sonarcloud.io -Dsonar.login=$SONAR"
        )

        run(
            name = "Test - Integration",
            command = "./mvnw -B verify -DskipUnitTests",
        )
    }

    job(
        id = "postgres-compatibility",
        runsOn = RunnerType.UbuntuLatest,
        strategyMatrix = mapOf(
            // note that 15 is tested already in the regular build job
            // removed others for now to improve throughput regarding actions
            "postgresVersion" to listOf("16"),
        ),
    ) {
        uses(
            name = "Checkout",
            action = Checkout(fetchDepth = Checkout.FetchDepth.Infinite)
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
            name = "Test - Integration",
            command = "./mvnw -B -Dpostgres.version=${expr("matrix.postgresVersion")} verify -DskipUnitTests",
        )
    }
}


