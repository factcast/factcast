/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.store;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.lang.NonNull;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Conditional(IsReadOnlyEnv.AnyNotation.class)
public @interface IsReadOnlyEnv {
  final class AnyNotation extends SpringBootCondition {

    @NonNull
    @Override
    public ConditionOutcome getMatchOutcome(
        ConditionContext context, AnnotatedTypeMetadata metadata) {

      Environment environment = context.getEnvironment();
      String prefix = StoreConfigurationProperties.PROPERTIES_PREFIX + ".";

      Boolean kebabCase = environment.getProperty(prefix + "read-only-mode-enabled", Boolean.class);

      Boolean camelCase = environment.getProperty(prefix + "readOnlyModeEnabled", Boolean.class);

      boolean readOnly = Boolean.TRUE.equals(kebabCase) || Boolean.TRUE.equals(camelCase);

      return readOnly
          ? ConditionOutcome.match("Read-only mode is enabled")
          : ConditionOutcome.noMatch("Read-only mode is not enabled");
    }
  }
}
