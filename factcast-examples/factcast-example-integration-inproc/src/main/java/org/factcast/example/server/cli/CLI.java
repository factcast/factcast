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
package org.factcast.example.server.cli;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CLI implements CommandLineRunner {

    @Autowired
    FactCast fcclient;

    @Override
    public void run(String... args) throws Exception {

        log.info(
                "**************************************** CLI ************************************");

        Fact f = Fact.builder().ns("ns").build("{}");

        fcclient.publish(f);

        SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("ns")).fromScratch();
        fcclient.subscribe(req, n -> {
            System.out.println(n);
        });
        Thread.sleep(1000);
    }

}
