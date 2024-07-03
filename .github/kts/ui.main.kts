#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:2.2.0")

import io.github.typesafegithub.workflows.actions.actions.CacheV4
import io.github.typesafegithub.workflows.actions.actions.CheckoutV4
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV4
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Maven UITest",
    on = listOf(
        PullRequest(),
        Push(
            branches = listOf("master"),
        ),
    ),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled

) {
    job(
        id = "build",
        runsOn = RunnerType.UbuntuLatest,
    ) {
        uses(
            name = "Checkout",
            action = CheckoutV4(fetchDepth = CheckoutV4.FetchDepth.Infinite),
        )
        uses(
            name = "Cache - Maven Repository",
            action = CacheV4(
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
            name = "JDK 17",
            action = SetupJavaV4(
                distribution = SetupJavaV4.Distribution.Corretto,
                javaVersion = "17",
            ),
        )

        run(
            name = "Build with Maven, no testing",
            command = "./mvnw -B clean install -DskipTests",
        )

        run(
            name = "Test - UI",
            command = "cd factcast-server-ui ; ../mvnw -B -Dui failsafe:integration-test ",
        )
    }
}
