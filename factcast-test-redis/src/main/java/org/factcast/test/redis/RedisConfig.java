package org.factcast.test.redis;

import java.lang.annotation.*;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface RedisConfig {
  String redisVersion() default Config.REDIS_VERSION;

  @Value
  @With
  class Config {
    String redisVersion;

    static final String REDIS_VERSION = "5.0.9-alpine";

    static Config defaults() {
      return new Config(REDIS_VERSION);
    }

    static Config from(@NonNull RedisConfig e) {
      return defaults().withRedisVersion(e.redisVersion());
    }
  }
}
