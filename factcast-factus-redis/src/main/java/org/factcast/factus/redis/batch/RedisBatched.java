package org.factcast.factus.redis.batch;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.val;
import org.redisson.api.BatchOptions;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface RedisBatched {

  int size() default 50;

  long responseTimeout() default Defaults.responseTimeout;

  int retryAttempts() default Defaults.retryAttempts;

  long retryInterval() default Defaults.retryInterval;

  class Defaults {
    static final long responseTimeout = 5000;
    static final int retryAttempts = 5;
    static final long retryInterval = 3000;

    public static BatchOptions create() {
      return BatchOptions.defaults()
          .responseTimeout(responseTimeout, TimeUnit.MILLISECONDS)
          .retryAttempts(retryAttempts)
          .retryInterval(retryInterval, TimeUnit.MILLISECONDS);
    }

    public static BatchOptions with(@Nullable RedisBatched batched) {
      val opts = create();

      if (batched != null) {

        long responseTimeout = batched.responseTimeout();
        if (responseTimeout > 0) {
          opts.responseTimeout(responseTimeout, TimeUnit.MILLISECONDS);
        }

        int retryAttempts = batched.retryAttempts();
        if (retryAttempts > 0) {
          opts.retryAttempts(retryAttempts);
        }

        long retryInterval = batched.retryInterval();
        if (retryInterval > 0) {
          opts.retryInterval(retryInterval, TimeUnit.MILLISECONDS);
        }
      }

      return opts;
    }
  }
}
