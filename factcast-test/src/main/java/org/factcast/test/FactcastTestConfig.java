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
package org.factcast.test;

import java.lang.annotation.*;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface FactcastTestConfig {
  String factcastVersion() default Config.FACTCAST_VERSION;

  String postgresVersion() default Config.POSTGRES_VERSION;

  String configDir() default Config.CONFIG_DIR;

  @Value
  @With
  class Config {
    String factcastVersion;
    String postgresVersion;
    String configDir;

    static final String FACTCAST_VERSION = "latest";
    static final String POSTGRES_VERSION = "13.3";
    static final String CONFIG_DIR = "./config";

    static Config defaults() {
      return new Config(FACTCAST_VERSION, POSTGRES_VERSION, CONFIG_DIR);
    }

    static Config from(@NonNull FactcastTestConfig e) {
      return defaults()
          .withConfigDir(e.configDir())
          .withFactcastVersion(e.factcastVersion())
          .withPostgresVersion(e.postgresVersion());
    }
  }
}
