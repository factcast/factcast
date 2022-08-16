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

import java.util.*;

import org.factcast.core.Fact;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserEmailsLowLevelProjectionTest {

  UserEmailsLowLevelProjection uut = new UserEmailsLowLevelProjection();

  @Test
  void whenHandlingUserAddedFactEmailIsAdded() {
    // arrange
    String jsonPayload =
        String.format("{\"id\":\"%s\", \"email\": \"%s\"}", UUID.randomUUID(), "user@bar.com");
    Fact userAdded =
        Fact.builder()
            .id(UUID.randomUUID())
            .ns("user")
            .type("UserAdded")
            .version(1)
            .build(jsonPayload);

    // act
    uut.handleUserAdded(userAdded);

    // assert
    Set<String> emails = uut.getUserEmails();
    assertThat(emails).hasSize(1).containsExactly("user@bar.com");
  }

  @Test
  void whenHandlingUserRemovedFactEmailIsRemoved() {
    UUID userId = UUID.randomUUID();

    Fact userAdded =
        Fact.builder()
            .id(UUID.randomUUID())
            .ns("user")
            .type("UserAdded")
            .version(1)
            .build(String.format("{\"id\":\"%s\", \"email\": \"%s\"}", userId, "user@bar.com"));

    Fact userRemoved =
        Fact.builder()
            .id(UUID.randomUUID())
            .ns("user")
            .type("UserRemoved")
            .version(1)
            .build(String.format("{\"id\":\"%s\"}", userId));

    uut.handleUserAdded(userAdded);
    uut.handleUserRemoved(userRemoved);
    Set<String> emails = uut.getUserEmails();

    assertThat(emails).isEmpty();
  }

  @Test
  void userAddedFactIsApplied() {
    UserEmailsLowLevelProjection projection = spy(new UserEmailsLowLevelProjection());
    String jsonPayload =
        String.format("{\"id\":\"%s\", \"email\": \"%s\"}", UUID.randomUUID(), "user@bar.com");
    Fact userAdded =
        Fact.builder()
            .id(UUID.randomUUID())
            .ns("user")
            .type("UserAdded")
            .version(1)
            .build(jsonPayload);

    projection.apply(userAdded);

    verify(projection).handleUserAdded(any(Fact.class));
  }

  @Test
  void userDeletedFactIsApplied() {
    UserEmailsLowLevelProjection projection = spy(new UserEmailsLowLevelProjection());
    Fact userRemoved =
        Fact.builder()
            .id(UUID.randomUUID())
            .ns("user")
            .type("UserRemoved")
            .version(1)
            .build(String.format("{\"id\":\"%s\"}", UUID.randomUUID()));

    projection.apply(userRemoved);

    verify(projection).handleUserRemoved(any(Fact.class));
  }
}
