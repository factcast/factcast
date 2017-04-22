package org.factcast.client.grpc;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.subscription.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

@SpringBootApplication
@EnableAutoConfiguration
@Configuration
public class SmokeMT {

	public static void main(String[] args) {
		Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.INFO);

		SpringApplication.run(SmokeMT.class);
	}

	@Component
	public static class SmokeMTCommandRunner implements CommandLineRunner {

		@Autowired
		FactCast fc;

		@Override
		public void run(String... args) throws Exception {

			Optional<Fact> fetchById = fc.fetchById(UUID.randomUUID());
			System.out.println(fetchById.isPresent());

			SmokeTestFact f1 = new SmokeTestFact().type("create");
			fc.publish(f1);

			fetchById = fc.fetchById(f1.id());
			System.out.println(fetchById.isPresent());

			SmokeTestFact m = new SmokeTestFact().type("withMark");
			UUID mark = fc.publishWithMark(m);

			System.out.println(fc.fetchById(m.id()).isPresent());
			System.out.println(fc.fetchById(mark).isPresent());

			fc.subscribeToIds(SubscriptionRequest.catchup(FactSpec.ns("default")).sinceInception(),
					System.err::println);

			fc.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default")).sinceInception(),
					System.err::println);
		}

	}
}
