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
package org.factcast.factus.serializer;

import org.factcast.factus.projection.SnapshotProjection;

public interface SnapshotSerializer {
  byte[] serialize(SnapshotProjection a);

  <A extends SnapshotProjection> A deserialize(Class<A> type, byte[] bytes);

  boolean includesCompression();

  /**
   * @return displayable name of the serializer. Make sure it is unique, as it is used as part of
   *     the snapshot key
   */
  String getId();
}
