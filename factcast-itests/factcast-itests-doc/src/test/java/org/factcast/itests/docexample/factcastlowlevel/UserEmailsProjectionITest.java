/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.itests.docexample.factcastlowlevel;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.test.FactCastExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ExtendWith(FactCastExtension.class)
class UserEmailsProjectionITest {

  @Autowired FactCast factCast;

  private final UserEmailsProjection uut = new UserEmailsProjection();

  private class FactObserverImpl implements FactObserver {

    @Override
    public void onNext(@NonNull Fact fact) {
      uut.apply(fact);
    }
  }

  @Test
  void projectionHandlesUserAddedFact() {
    UUID userId = UUID.randomUUID();
    Fact userAdded =
        Fact.builder()
            .id(UUID.randomUUID())
            .ns("user")
            .type("UserAdded")
            .version(1)
            .build(String.format("{\"id\":\"%s\", \"email\": \"%s\"}", userId, "user@bar.com"));

    factCast.publish(userAdded);

    SubscriptionRequest subscriptionRequest =
        SubscriptionRequest.catchup(FactSpec.ns("user").type("UserAdded"))
            .or(FactSpec.ns("user").type("UserRemoved"))
            .fromScratch();

    factCast.subscribe(subscriptionRequest, new FactObserverImpl()).awaitComplete();

    Set<String> userEmails = uut.getUserEmails();
    assertThat(userEmails).hasSize(1).containsExactly("user@bar.com");
  }

  @Test
  void projectionHandlesUserAddedAndUserRemovedFacts() {
    UUID userId1 = UUID.randomUUID();
    UUID userId2 = UUID.randomUUID();

    Fact user1Added =
        Fact.builder()
            .id(UUID.randomUUID())
            .ns("user")
            .type("UserAdded")
            .version(1)
            .build(String.format("{\"id\":\"%s\", \"email\": \"%s\"}", userId1, "user1@bar.com"));

    Fact user2Added =
        Fact.builder()
            .id(UUID.randomUUID())
            .ns("user")
            .type("UserAdded")
            .version(1)
            .build(String.format("{\"id\":\"%s\", \"email\": \"%s\"}", userId2, "user2@bar.com"));

    Fact user2Removed =
        Fact.builder()
            .id(UUID.randomUUID())
            .ns("user")
            .type("UserRemoved")
            .version(1)
            .build(String.format("{\"id\":\"%s\"}", userId2));

    factCast.publish(List.of(user1Added, user2Added, user2Removed));

    SubscriptionRequest subscriptionRequest =
        SubscriptionRequest.catchup(FactSpec.ns("user").type("UserAdded"))
            .or(FactSpec.ns("user").type("UserRemoved"))
            .fromScratch();

    factCast.subscribe(subscriptionRequest, new FactObserverImpl()).awaitComplete();
    Set<String> userEmails = uut.getUserEmails();

    assertThat(userEmails).hasSize(1).containsExactly("user1@bar.com");
  }
}
