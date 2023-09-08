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

  String postgresVersion() default "";

  String configDir() default Config.CONFIG_DIR;

  boolean securityEnabled() default false;

  @Value
  @With
  class Config {
    String factcastVersion;
    String postgresVersion;
    String configDir;
    boolean securityEnabled;

    static final String FACTCAST_VERSION = "latest";
    static final String CONFIG_DIR = "./config";

    static Config defaults() {
      return new Config(FACTCAST_VERSION, PostgresVersion.get(), CONFIG_DIR, false);
    }

    static Config from(@NonNull FactcastTestConfig e) {
      final Config config =
          defaults().withConfigDir(e.configDir()).withFactcastVersion(e.factcastVersion());

      if (!e.postgresVersion().isEmpty()) {
        return config.withPostgresVersion(e.postgresVersion());
      }

      return config;
    }
  }
}
