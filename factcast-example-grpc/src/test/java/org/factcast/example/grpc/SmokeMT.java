package org.factcast.example.grpc;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.subscription.FactObserver;
import org.factcast.core.subscription.FactSpec;
import org.factcast.core.subscription.Subscription;
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

            AtomicLong l = new AtomicLong();
            CompletableFuture<Subscription> sub = fc.subscribeToFacts(SubscriptionRequest.follow(
                    FactSpec.ns("default")).sinceInception(), new FactObserver() {

                        @Override
                        public void onNext(Fact f) {
                            l.incrementAndGet();

                            try {
                                if (Math.random() < .001) {
                                    System.out.println(l.get());
                                    Thread.sleep(50);
                                }
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }

                        }

                        public void onComplete() {
                            System.out.println("COMPLETE");
                        };
                    });

            System.out.println("writing");

            fc.publish(new SmokeTestFact().aggId(aggId));

            Thread.sleep(500);

            System.out.println("closing");
            sub.get().close();
            Thread.sleep(500);

            System.out.println("publishing one more");
            Fact afterClose = new SmokeTestFact().aggId(aggId);
            fc.publish(afterClose);

            Thread.sleep(3000);

        }

    }
}
