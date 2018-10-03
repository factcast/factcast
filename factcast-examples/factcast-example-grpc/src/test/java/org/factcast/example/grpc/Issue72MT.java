package org.factcast.example.grpc;

import java.util.Optional;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class Issue72MT implements CommandLineRunner {

    @NonNull
    private final FactCast fc;

    @Override
    public void run(String... args) throws Exception {

        SmokeTestFact fact = new SmokeTestFact().type("foo").jsonPayload("{\"bla\":\"fasel\"}");
        fc.publish(fact);
        System.out.println("published " + fact);

        Optional<Fact> fetchById = fc.fetchById(fact.id());
        System.out.println("fetch by id returns payload:" + fetchById.get().jsonPayload());

        Subscription sub = fc.subscribeToIds(SubscriptionRequest.follow(FactSpec.ns("smoke"))
                .fromScratch(),
                f -> System.out.println(f)).awaitCatchup(5000);

        //sub.close();

    }

}
