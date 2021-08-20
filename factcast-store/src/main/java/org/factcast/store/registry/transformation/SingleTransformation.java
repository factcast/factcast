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
package org.factcast.store.registry.transformation;

import java.util.Optional;
import lombok.NonNull;
import lombok.Value;

@Value
public class SingleTransformation implements Transformation {
  @NonNull TransformationKey key;

  int fromVersion;

  int toVersion;

  @NonNull Optional<String> transformationCode;

  public static Transformation of(@NonNull TransformationSource source, String transformation) {
    return new SingleTransformation(
        source.toKey(), source.from(), source.to(), Optional.ofNullable(transformation));
  }

  public static Transformation of(@NonNull TransformationKey key, int from, int to, String code) {
    return new SingleTransformation(key, from, to, Optional.ofNullable(code));
  }

  public static Transformation empty(@NonNull TransformationKey key, int from, int to) {
    return new SingleTransformation(key, from, to, Optional.empty());
  }
}
