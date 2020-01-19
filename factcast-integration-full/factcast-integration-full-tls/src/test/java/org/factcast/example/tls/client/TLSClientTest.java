package org.factcast.example.tls.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TLSClientTest {
    @ClassRule
    public static DockerComposeContainer<?> compose = new DockerComposeContainer<>(
            new File("docker-compose.yml"))
                    .withExposedService("db", 5432, new HostPortWaitStrategy())
                    .withExposedService("factcast", 9443,
                            new HostPortWaitStrategy());

    @Autowired
    FactCast fc;

    @Test
    public void simplePublishRoundtrip() throws Exception {
        Fact fact = Fact.builder().ns("smoke").type("foo").build("{\"bla\":\"fasel\"}");
        fc.publish(fact);

        try (Subscription sub = fc.subscribe(SubscriptionRequest.follow(FactSpec.ns("smoke"))
                .fromScratch(),
                f -> {
                    assertEquals(fact.ns(), f.ns());
                    assertEquals(fact.type(), f.type());
                    assertEquals(fact.id(), f.id());
                }).awaitCatchup(1000);) {
        }
    }
}
