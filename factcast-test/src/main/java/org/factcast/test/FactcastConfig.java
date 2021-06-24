package org.factcast.test;

import java.lang.annotation.*;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface FactcastConfig {
  String factcastVersion() default Config.FACTCAST_VERSION;

  String postgresVersion() default Config.POSTGRES_VERSION;

  String configDir() default Config.CONFIG_DIR;

  @Value
  @With
  class Config {
    String factcastVersion;
    String postgresVersion;
    String configDir;

    static final String FACTCAST_VERSION = "0.3.9";
    static final String POSTGRES_VERSION = "11.5";
    static final String CONFIG_DIR = "./config";

    static Config defaults() {
      return new Config(FACTCAST_VERSION, POSTGRES_VERSION, CONFIG_DIR);
    }

    static Config from(@NonNull FactcastConfig e) {
      return defaults()
          .withConfigDir(e.configDir())
          .withFactcastVersion(e.factcastVersion())
          .withPostgresVersion(e.postgresVersion());
    }
  }
}
