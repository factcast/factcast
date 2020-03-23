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

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
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

        // // read it back and let factcast transform it to version 3
        // Subscription sub = fc
        // .subscribe(SubscriptionRequest.catchup(FactSpec.from(UserCreated.class)).fromScratch(),
        // e -> {
        // UserCreated p = FactCastJson.readValue(UserCreated.class,
        // e.jsonPayload());
        // System.out.println(p);
        // })
        // .awaitCatchup(5000);
        //
        // sub.close();

    }

}
