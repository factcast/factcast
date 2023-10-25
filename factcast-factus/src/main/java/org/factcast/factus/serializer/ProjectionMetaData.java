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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SuppressWarnings("java:S1133")
public @interface ProjectionMetaData {
  String name() default "";

  /**
   * use revision instead. Will be removed in 0.8
   *
   * @deprecated
   */
  @Deprecated
  long serial() default 0;

  /** will be made required as soon as serial() is removed */
  long revision() default Long.MIN_VALUE;

  @UtilityClass
  class Resolver {
    public static Optional<ProjectionMetaData> resolveFor(@NonNull Class<?> clazz) {
      return Optional.ofNullable(clazz.getAnnotation(ProjectionMetaData.class));
    }
  }

  @UtilityClass
  @SuppressWarnings({"java:S1874"})
  class Revision {
    public static long get(ProjectionMetaData ann) {
      if (ann.revision() == Long.MIN_VALUE) return ann.serial();
      else return ann.revision();
    }
  }
}
