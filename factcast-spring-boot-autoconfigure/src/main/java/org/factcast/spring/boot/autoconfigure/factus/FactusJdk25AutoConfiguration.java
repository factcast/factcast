/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.spring.boot.autoconfigure.factus;

import org.factcast.factus.lock.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.system.JavaVersion;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
// once supported by spring, replace by: @ConditionalOnJava(JavaVersion.TWENTY_FIVE)
@ConditionalOnJava(JavaVersion.TWENTY_FOUR)
@ConditionalOnClass(
    name = "java.lang.ScopedValue",
    value = InLockedOperationForVirtualThreads.class)
// needs to come before FactusAutoConfiguration
@AutoConfigureBefore(FactusAutoConfiguration.class)
public class FactusJdk25AutoConfiguration {
  @Bean
  @ConditionalOnMissingClass
  public InLockedOperation inLockedOperationForVirtualThreads() {
    return new InLockedOperationForVirtualThreads();
  }
}
