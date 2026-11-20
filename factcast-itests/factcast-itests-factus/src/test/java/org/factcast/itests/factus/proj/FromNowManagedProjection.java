/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.itests.factus.proj;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.NonNull;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;

@ProjectionMetaData(revisionId = "1")
public class FromNowManagedProjection extends LocalManagedProjection implements UserNames {

  private final Map<UUID, String> userNames = new HashMap<>();

  @Override
  @NonNull
  public InitialPosition initialPosition() {
    return InitialPosition.FROM_NOW;
  }

  @Override
  public Map<UUID, String> userNames() {
    return this.userNames;
  }
}
