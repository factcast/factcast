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
package org.factcast.factus.projection;

import java.util.UUID;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * indirection to/from the aggregate's id, so that it does not spoil the public interface of
 * Aggregate, in case you'd want to use wrapping or inline classes when exposing the Id.
 */
@UtilityClass
public class AggregateUtil {
  public static UUID aggregateId(@NonNull Aggregate a) {
    return a.aggregateId();
  }

  public static void aggregateId(@NonNull Aggregate a, @NonNull UUID idToSet) {
    if (a.aggregateId() != null) {
      throw new IllegalStateException("aggregateId is already set and not supposed to change.");
    }
    a.aggregateId(idToSet);
  }
}
