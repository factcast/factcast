package org.factcast.example.grpc;

import java.util.UUID;

import org.factcast.core.FactCast;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import lombok.RequiredArgsConstructor;

@SpringBootApplication
@EnableAutoConfiguration
@Configuration
@SuppressWarnings("unused")
public class MachineGunMT {

    public static void main(String[] args) {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.ERROR);

        SpringApplication.run(MachineGunMT.class);
    }

    @RequiredArgsConstructor
    @Component
    public static class SmokeMTCommandRunner implements CommandLineRunner {

        final FactCast fc;

        private int count = 0;

        @Override
        public void run(String... args) throws Exception {

            for (;;) {
                UUID aggId = UUID.randomUUID();
                final SmokeTestFact first = new SmokeTestFact();
                fc.publish(first.aggId(aggId));
                // Thread.sleep(100);
                // if (++count % 1024 == 0) {
                // System.out.print(".");
                // }
            }

        }

    }
}
