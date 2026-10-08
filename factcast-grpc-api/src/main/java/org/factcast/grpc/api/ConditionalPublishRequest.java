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
package org.factcast.grpc.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import lombok.Value;
import org.factcast.core.Fact;
import org.factcast.core.store.StateToken;

@Value
public class ConditionalPublishRequest {
  @NonNull List<? extends Fact> facts;

  private UUID token;

  public Optional<StateToken> token() {
    if (token == null) {
      return Optional.empty();
    } else {
      return Optional.of(new StateToken(token));
    }
  }
}
