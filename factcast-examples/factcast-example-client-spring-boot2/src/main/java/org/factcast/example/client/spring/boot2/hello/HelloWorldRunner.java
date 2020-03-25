/*
 * Copyright © 2017-2020 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.example.client.spring.boot2.hello;

import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.util.FactCastJson;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class HelloWorldRunner implements CommandLineRunner {

    @NonNull
    private final FactCast fc;

    @Override
    public void run(String... args) throws Exception {

        Fact fact = Fact.builder()
                .ns("Users")
                .type("UserCreated")
                .version(1)
                .id(UUID.randomUUID())
                .build("{ \"firstName\":\"Horst\",\"lastName\":\"Lichter\"}");
        fc.publish(fact);
        System.out.println("published " + fact);

        fact = Fact.builder()
                .ns("Users")
                .type("UserCreated")
                .version(3)
                .id(UUID.randomUUID())
                .build("{\"firstName\":\"Horst\",\"lastName\":\"Lichter\",\"displayName\":\"Horsti\",\"salutation\":\"Mr\"}");
        fc.publish(fact);
        System.out.println("published " + fact);

        // read it back and let factcast transform it to version 3
        fetch(UserCreated.class, p -> {
            System.err.println(p);
        });

        // read it back and let factcast transform it to version 1
        fetch(UserCreatedV1.class, p -> {
            System.err.println(p);
        });

    }

    private <T> void fetch(Class<T> class1, Consumer<T> c) throws TimeoutException, Exception {

        Subscription sub;
        sub = fc
                .subscribe(SubscriptionRequest.catchup(FactSpec.from(class1))
                        .fromScratch(),
                        e -> c.accept(FactCastJson.readValue(class1,
                                e.jsonPayload())))
                .awaitCatchup(5000);

        sub.close();
    }

}
