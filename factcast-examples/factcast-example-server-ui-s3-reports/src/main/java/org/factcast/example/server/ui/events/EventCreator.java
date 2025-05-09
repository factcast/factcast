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
package org.factcast.example.server.ui.events;

import com.google.common.collect.Lists;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class EventCreator implements CommandLineRunner {

  @NonNull private final FactCast fc;

  @Override
  public void run(String... args) throws Exception {

    val factId1 = UUID.randomUUID();
    val firstNameUuid = UUID.randomUUID();
    Fact created =
        Fact.builder()
            .ns("users")
            .type("UserCreated")
            .version(1)
            .id(factId1)
            .build("{\"firstName\":\"" + firstNameUuid + "\",\"lastName\":\"Lichter\"}");

    val factId2 = UUID.randomUUID();
    Fact changed =
        Fact.builder()
            .ns("users")
            .type("UserChanged")
            .version(1)
            .id(factId2)
            .build("{\"firstName\":\"" + firstNameUuid + "\",\"lastName\":\"Lauch\"}");

    val factId3 = UUID.randomUUID();
    val firstNameUuid2 = UUID.randomUUID();
    Fact created2 =
        Fact.builder()
            .ns("users")
            .type("UserCreated")
            .version(1)
            .id(factId3)
            .build("{\"firstName\":\"" + firstNameUuid2 + "\",\"lastName\":\"August\"}");

    fc.publish(Lists.newArrayList(created, changed, created2));
  }
}
