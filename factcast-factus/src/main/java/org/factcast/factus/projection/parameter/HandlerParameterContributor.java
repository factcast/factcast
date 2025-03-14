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
package org.factcast.factus.projection.parameter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.NonNull;

public interface HandlerParameterContributor {
  /**
   * @return null if provider cannot be created
   */
  @Nullable
  // ENHANCEMENT add parameterName?
  HandlerParameterProvider providerFor(
      @NonNull Class<?> type, @Nullable Type genericType, @NonNull Set<Annotation> annotations);
}
