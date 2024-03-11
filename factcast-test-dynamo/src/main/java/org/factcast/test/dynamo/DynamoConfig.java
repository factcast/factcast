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
package org.factcast.test.dynamo;

import java.lang.annotation.*;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface DynamoConfig {
  String redisVersion() default Config.DYNAMO_VERSION;

  @Value
  @With
  class Config {
    String dynamoVersion;

    static final String DYNAMO_VERSION = "2.2.1";

    static Config defaults() {
      return new Config(DYNAMO_VERSION);
    }

    static Config from(@NonNull DynamoConfig e) {
      return defaults().withDynamoVersion(e.redisVersion());
    }
  }
}
