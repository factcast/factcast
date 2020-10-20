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
   * In order to catch changes when a {@link SnapshotProjection} got changed, calculate a hash that
   * changes when the schema of the serialised class changes.
   *
   * <p>Note that in some cases, it is possible to add fields and use serializer-specific means to
   * ignore them for serialization (e.g. by using @JsonIgnore with Jackson).
   *
   * <p>Hence, every serializer is asked to calculate it's own hash, that should only change in case
   * changes to the projection where made that were relevant for deserialization.
   *
   * <p>This method is only used if no other means of providing a hash is used. Alternatives are
   * using the @ProjectionSerial annotation or defining a final static long field called
   * serialVersionUID.
   *
   * <p>Note, that the serial will be cached per class
   *
   * @param projectionClass the snapshot projection class to calculate the hash for
   * @return the calculated hash or null, if no hash could be calculated (makes snapshotting fail if
   *     no other means of providing a hash is used)
   */
  Long calculateProjectionSerial(Class<? extends SnapshotProjection> projectionClass);

  /**
   * @return displayable name of the serializer. Make sure it is unique, as it is used as part of
   *     the snapshot key
   */
  String getId();
}
