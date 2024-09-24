/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.server.ui.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Serializable;
import java.util.List;
import lombok.NonNull;
import org.factcast.core.Fact;

public record Report(
    @NonNull String name, @NonNull String json, @NonNull String query, int resultCount)
    implements Serializable {

  public Report(@NonNull String name, @NonNull List<Fact> facts, @NonNull String query) {
    this(name, getJsonFromFacts(facts), query, facts.size());
  }

  static String getJsonFromFacts(List<Fact> facts) {
    final var objectMapper = new ObjectMapper();
    try {
      return objectMapper.writeValueAsString(facts);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
