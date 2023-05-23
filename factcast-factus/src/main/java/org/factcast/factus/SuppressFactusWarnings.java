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
package org.factcast.factus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import lombok.NonNull;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
public @interface SuppressFactusWarnings {
  Warning[] value();

  String note() default "";

  enum Warning {
    ALL,
    PUBLIC_HANDLER_METHOD;

    public boolean isSuppressed(SuppressFactusWarnings annotation) {
      if (annotation == null) {
        return false;
      } else {
        return Arrays.stream(annotation.value()).anyMatch(w -> w == ALL || w == this);
      }
    }

    public boolean isSuppressedOn(@NonNull Class<?> c) {
      return isSuppressed(c.getAnnotation(SuppressFactusWarnings.class));
    }

    public boolean isSuppressedOn(@NonNull Method m) {
      return isSuppressed(m.getAnnotation(SuppressFactusWarnings.class))
          || isSuppressedOn(m.getDeclaringClass());
    }

    public boolean isSuppressedOn(@NonNull Field f) {
      return isSuppressed(f.getAnnotation(SuppressFactusWarnings.class))
          || isSuppressedOn(f.getDeclaringClass());
    }
  }
}
