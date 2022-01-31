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
package org.factcast.factus.projection;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.serializer.ProjectionMetaData.Resolver;
import org.factcast.factus.utils.ClassUtils;

@RequiredArgsConstructor(staticName = "of")
@ToString(of = "key")
public class ScopedName {
  private static final String NAME_SEPARATOR = "_";

  private final String key;

  public static ScopedName fromProjectionMetaData(Class<?> clazz) {
    ProjectionMetaData metaData =
        Resolver.resolveFor(clazz)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        clazz.getName()
                            + " must be annotated by "
                            + ProjectionMetaData.class.getName()));

    String name = metaData.name();
    if (name.isEmpty()) {
      name = ClassUtils.getNameFor(clazz);
    }

    return ScopedName.of(name + NAME_SEPARATOR + metaData.serial());
  }

  public static ScopedName of(@NonNull String name, long serial) {
    return ScopedName.of(name + NAME_SEPARATOR + serial);
  }

  public ScopedName with(@NonNull String postfix) {
    if (postfix.trim().isEmpty()) {
      throw new IllegalArgumentException("postfix must not be empty");
    }
    return ScopedName.of(key + NAME_SEPARATOR + postfix);
  }

  public String asString() {
    return key;
  }
}
