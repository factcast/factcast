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
package org.factcast.factus.redis.batch;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import lombok.experimental.UtilityClass;
import org.redisson.api.BatchOptions;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface RedisBatched {

  int bulkSize() default 50;

  long responseTimeout() default Defaults.responseTimeout;

  int retryAttempts() default Defaults.retryAttempts;

  long retryInterval() default Defaults.retryInterval;

  @UtilityClass
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
      BatchOptions opts = create();

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
