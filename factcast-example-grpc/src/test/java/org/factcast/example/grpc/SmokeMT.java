package org.factcast.example.grpc;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
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
@SuppressWarnings("unused")
public class SmokeMT {

    public static void main(String[] args) {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);

        SpringApplication.run(SmokeMT.class);
    }

    @RequiredArgsConstructor
    @Component
    public static class SmokeMTCommandRunner implements CommandLineRunner {

        final FactCast fc;

        @Override
        public void run(String... args) throws Exception {
            final MetricsRegistry r = new MetricsRegistry();
            ConsoleReporter consoleReporter = new ConsoleReporter(r, System.err,
                    ((MetricPredicate) (n, me) -> true));
            Meter readMeter = r.newMeter(this.getClass(), "readFromRemoteStore", "read",
                    TimeUnit.SECONDS);
            Meter writeMeter = r.newMeter(this.getClass(), "writeToRemoteStore", "written",
                    TimeUnit.SECONDS);
            // consoleReporter.start(1, TimeUnit.SECONDS);

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

            UUID aggId = UUID.randomUUID();

            // System.out.println("w 1000");
            //
            // for (int i = 0; i < 10; i++) {
            //
            // List<Fact> p = new ArrayList<>(1000);
            // for (int j = 0; j < 1000; j++) {
            // p.add(new SmokeTestFact().aggId(aggId));
            // }
            // System.out.println("pub 1000");
            // fc.publish(p);
            //
            // }

            System.out.println("reading");

            AtomicLong l = new AtomicLong();
            long start = System.currentTimeMillis();
            Subscription sub = fc.subscribeToFacts(SubscriptionRequest.follow(FactSpec.ns("smoke"))
                    .fromScratch(), new FactObserver() {

                        @Override
                        public void onNext(Fact f) {
                            l.incrementAndGet();
                        }

                    }).awaitCatchup();

            System.out.println("l " + l.get() + " " + (System.currentTimeMillis() - start) + "ms");

            // System.out.println("writing");
            //
            // final SmokeTestFact first = new SmokeTestFact();
            // fc.publish(first.aggId(aggId));
            // fc.publish(new SmokeTestFact().aggId(aggId));
            // fc.publish(new SmokeTestFact().aggId(aggId));
            // fc.publish(Arrays.asList(new SmokeTestFact().aggId(aggId), new
            // SmokeTestFact().aggId(
            // aggId), new SmokeTestFact().aggId(aggId)));
            //
            // fc.fetchById(first.id());
            //
            // Thread.sleep(500);
            //
            // System.out.println("closing");
            // // sub.close();
            // Thread.sleep(500);
            //
            // fc.subscribeToFacts(SubscriptionRequest.follow(FactSpec.ns("smoke").aggId(UUID
            // .randomUUID())).fromScratch(), f -> {
            // }).awaitCatchup();
            //
            // fc.subscribeToFacts(SubscriptionRequest.follow(FactSpec.ns("smoke").aggId(UUID
            // .randomUUID())).fromScratch(), f -> {
            // }).awaitCatchup();
            //
            // fc.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("smoke").aggId(UUID
            // .randomUUID())).fromScratch(), f -> {
            // }).awaitCatchup();
            //
            // System.out.println("publishing one more");
            // Fact afterClose = new SmokeTestFact().aggId(aggId);
            // fc.publish(afterClose);
            //
            // Thread.sleep(3000);

        }

    }
}
