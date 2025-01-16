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
package org.factcast.factus.event;

import java.util.*;

/** EventObjects are expected to be annotated with @{@link Specification}. */
public interface EventObject {

  /**
   * @return KV-Map that does not allow for multiple values per key
   * @deprecated Please implement additionalMeta() instead (sorry for the naming)
   */
  @Deprecated
  default Map<String, String> additionalMetaMap() {
    return Collections.emptyMap();
  }

  /**
   * if we hit this impl, that means that additionalMetaMap() is probably implemented. We'd want to
   * change it of course, once we can make sure all generated Events implement this method instead.
   */
  default MetaMap additionalMeta() {
    return MetaMap.from(additionalMetaMap());
  }

  Set<UUID> aggregateIds();
}
