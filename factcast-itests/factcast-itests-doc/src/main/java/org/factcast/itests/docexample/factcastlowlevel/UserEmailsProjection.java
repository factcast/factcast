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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;

@Slf4j
public class UserEmailsProjection {

  private final Map<UUID, String> userEmails = new HashMap<>();

  @NonNull
  public Set<String> getUserEmails() {
    return new HashSet<>(userEmails.values());
  }

  public void apply(Fact fact) {
    switch (fact.type()) {
      case "UserAdded":
        handleUserAdded(fact);
        break;
      case "UserRemoved":
        handleUserRemoved(fact);
        break;
      default:
        log.error("Fact type {} not supported", fact.type());
        break;
    }
  }

  @VisibleForTesting
  void handleUserAdded(Fact fact) {
    JsonNode payload = parsePayload(fact);
    userEmails.put(extractIdFrom(payload), extractEmailFrom(payload));
  }

  @VisibleForTesting
  void handleUserRemoved(Fact fact) {
    JsonNode payload = parsePayload(fact);
    userEmails.remove(extractIdFrom(payload));
  }

  // helper methods:

  @SneakyThrows
  private JsonNode parsePayload(Fact fact) {
    return FactCastJson.readTree(fact.jsonPayload());
  }

  private UUID extractIdFrom(JsonNode payload) {
    return UUID.fromString(payload.get("id").asText());
  }

  private String extractEmailFrom(JsonNode payload) {
    return payload.get("email").asText();
  }
}
