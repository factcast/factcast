package org.factcast.factus.redis.tx;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.val;
import org.redisson.api.TransactionOptions;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface RedisTransactional {
  int size() default 50;

  long timeout() default Defaults.timeout;

  long responseTimeout() default Defaults.responseTimeout;

  int retryAttempts() default Defaults.retryAttempts;

  long retryInterval() default Defaults.retryInterval;

  class Defaults {
    static final long timeout = 30000;
    static final long responseTimeout = 5000;
    static final int retryAttempts = 5;
    static final long retryInterval = 3000;

    public static TransactionOptions create() {
      return TransactionOptions.defaults()
          .timeout(timeout, TimeUnit.MILLISECONDS)
          .responseTimeout(responseTimeout, TimeUnit.MILLISECONDS)
          .retryAttempts(retryAttempts)
          .retryInterval(retryInterval, TimeUnit.MILLISECONDS);
    }

    public static TransactionOptions with(@Nullable RedisTransactional transactional) {
      val opts = create();

      if (transactional != null) {

        long responseTimeout = transactional.responseTimeout();
        if (responseTimeout > 0) {
          opts.responseTimeout(responseTimeout, TimeUnit.MILLISECONDS);
        }

        int retryAttempts = transactional.retryAttempts();
        if (retryAttempts > 0) {
          opts.retryAttempts(retryAttempts);
        }

        long retryInterval = transactional.retryInterval();
        if (retryInterval > 0) {
          opts.retryInterval(retryInterval, TimeUnit.MILLISECONDS);
        }

        long timeout = transactional.timeout();
        if (timeout > 0) {
          opts.timeout(timeout, TimeUnit.MILLISECONDS);
        }
      }

      return opts;
    }
  }
}
