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

import java.util.*;

import org.factcast.factus.Handler;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.itests.docexample.factus.event.UserAdded;
import org.factcast.itests.docexample.factus.event.UserRemoved;

public class UserEmailsFactusProjection extends LocalManagedProjection {

  private final Map<UUID, String> userEmails = new HashMap<>();

  public Set<String> getEmails() {
    return new HashSet<>(userEmails.values());
  }

  @Handler
  void apply(UserAdded event) {
    userEmails.put(event.getUserId(), event.getEmail());
  }

  @Handler
  void apply(UserRemoved event) {
    userEmails.remove(event.getUserId());
  }
}
