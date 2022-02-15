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

import java.util.Set;
import java.util.UUID;
import org.factcast.itests.docexample.factus.event.UserAdded;
import org.factcast.itests.docexample.factus.event.UserRemoved;
import org.junit.jupiter.api.Test;

class UserEmailsProjectionTest {

  UserEmailsProjection uut = new UserEmailsProjection();
  UUID someUserId = UUID.randomUUID();

  @Test
  void whenHandlingUserAddedEventEmailIsAdded() {
    uut.apply(UserAdded.of(someUserId, "foo@bar.com"));
    Set<String> emails = uut.getEmails();

    assertThat(emails).hasSize(1).containsExactly("foo@bar.com");
  }

  @Test
  void whenHandlingUserRemovedEventEmailIsRemoved() {
    uut.apply(UserAdded.of(someUserId, "foo@bar.com"));
    uut.apply(UserRemoved.of(someUserId));

    Set<String> emails = uut.getEmails();
    assertThat(emails).isEmpty();
  }
}
