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
import java.util.*;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import org.factcast.core.util.MavenHelper;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface FactcastTestConfig {
  String factcastVersion() default "";

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

    static final String CONFIG_DIR = "./config";

    static Config defaults() {
      return new Config(FactcastVersion.get(), PostgresVersion.get(), CONFIG_DIR, false);
    }

    public String factcastVersion() {
      return resolve(factcastVersion);
    }

    static Config from(@NonNull FactcastTestConfig e) {
      Config config = defaults().withConfigDir(e.configDir());

      if (!e.factcastVersion().isEmpty()) {
        config = config.withFactcastVersion(e.factcastVersion());
      }
      if (!e.postgresVersion().isEmpty()) {
        config = config.withPostgresVersion(e.postgresVersion());
      }

      return config;
    }

    private static String resolve(@NonNull String versionAsAnnotated) {
      if (versionAsAnnotated.isEmpty()) {
        if (runFromJar()) return jarVersion();
        else return "latest";
      } else return versionAsAnnotated;
    }

    private static String jarVersion() {
      return MavenHelper.getVersion("factcast-test", FactcastTestConfig.class)
          .orElseThrow(() -> new IllegalStateException("Cannot retrieve current version."));
    }

    private static boolean runFromJar() {
      return Objects.requireNonNull(
              FactcastTestConfig.class.getResource(
                  "/" + FactcastTestConfig.class.getName().replace(".", "/") + ".class"))
          .toString()
          .startsWith("jar:");
    }
  }
}
