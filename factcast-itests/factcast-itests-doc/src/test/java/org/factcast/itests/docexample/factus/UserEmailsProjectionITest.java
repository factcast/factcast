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
package org.factcast.itests.docexample.factus;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.factcast.factus.Factus;
import org.factcast.itests.docexample.factus.event.UserAdded;
import org.factcast.itests.docexample.factus.event.UserRemoved;
import org.factcast.test.FactCastExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ExtendWith(FactCastExtension.class)
@Tag("integration")
public class UserEmailsProjectionITest {

  @Autowired Factus factus;

  UserEmailsProjection uut = new UserEmailsProjection();

  @Test
  void projectionHandlesUserAddedEvent() {
    UserAdded userAdded = UserAdded.of(UUID.randomUUID(), "user@bar.com");
    factus.publish(userAdded);
    factus.update(uut);

    Set<String> emails = uut.getEmails();
    assertThat(emails).hasSize(1).containsExactly("user@bar.com");
  }

  @Test
  void projectionHandlesUserAddedAndUserRemovedEvents() {
    UUID userId1 = UUID.randomUUID();
    UUID userId2 = UUID.randomUUID();
    factus.publish(
        List.of(
            UserAdded.of(userId1, "user1@bar.com"),
            UserAdded.of(userId2, "user2@bar.com"),
            UserRemoved.of(userId2)));

    factus.update(uut);

    Set<String> emails = uut.getEmails();
    assertThat(emails).hasSize(1).containsExactly("user1@bar.com");
  }
}
