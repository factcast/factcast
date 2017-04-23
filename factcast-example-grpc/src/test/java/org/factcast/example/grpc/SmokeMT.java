package org.factcast.example.grpc;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.factcast.client.cache.CachingFactCast;
import org.factcast.core.subscription.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricPredicate;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.reporting.ConsoleReporter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import lombok.RequiredArgsConstructor;

@SpringBootApplication
@EnableAutoConfiguration
@Configuration

public class SmokeMT {

	public static void main(String[] args) {
		Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.INFO);

		SpringApplication.run(SmokeMT.class);
	}

	@RequiredArgsConstructor
	@Component
	public static class SmokeMTCommandRunner implements CommandLineRunner {

		final CachingFactCast fc;

		@Override
		public void run(String... args) throws Exception {
			final MetricsRegistry r = new MetricsRegistry();
			ConsoleReporter consoleReporter = new ConsoleReporter(r, System.err, ((MetricPredicate) (n, me) -> true));
			consoleReporter.start(1, TimeUnit.SECONDS);

			// Optional<Fact> fetchById = fc.fetchById(UUID.randomUUID());
			// System.out.println(fetchById.isPresent());
			//
			// SmokeTestFact f1 = new SmokeTestFact().type("create");
			// fc.publish(f1);
			//
			// fetchById = fc.fetchById(f1.id());
			// System.out.println(fetchById.isPresent());
			//
			// SmokeTestFact m = new SmokeTestFact().type("withMark");
			// UUID mark = fc.publishWithMark(m);
			//
			// System.out.println(fc.fetchById(m.id()).isPresent());
			// System.out.println(fc.fetchById(mark).isPresent());
			//
			// fc.subscribeToIds(SubscriptionRequest.catchup(FactSpec.ns("default")).sinceInception(),
			// System.err::println);

			// final UUID since =
			// UUID.fromString("9224fe02-ee8b-4322-b8dd-b083001f4967");
			// fc.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default")).since(since),
			// System.err::println);
			//
			// System.err.println(
			// "--------------------------------------------------------------------------------------------");
			// fc.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default")).since(since),
			// System.err::println);
			//
			// System.err.println(
			// "--------------------------------------------------------------------------------------------");
			// fc.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default")).since(since),
			// System.err::println);

			Meter readMeter = r.newMeter(this.getClass(), "readFromRemoteStore", "read", TimeUnit.SECONDS);

			fc.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default")).sinceInception(), f -> {
				readMeter.mark();
			});

			Meter writeMeter = r.newMeter(this.getClass(), "writeToRemoteStore", "written", TimeUnit.SECONDS);
			while (true) {

				try {
					List<SmokeTestFact> ten = Arrays.asList(new SmokeTestFact(), new SmokeTestFact(),
							new SmokeTestFact(),

							new SmokeTestFact(), new SmokeTestFact(), new SmokeTestFact(), new SmokeTestFact(),
							new SmokeTestFact(), new SmokeTestFact(), new SmokeTestFact());

					fc.publish(ten);
					writeMeter.mark(10);
				} catch (Throwable e) {
					System.err.println(e);
					Thread.sleep(1000);
				}
			}

		}

	}
}
