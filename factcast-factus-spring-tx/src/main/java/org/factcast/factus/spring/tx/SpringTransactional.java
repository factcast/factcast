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
package org.factcast.factus.spring.tx;

import java.lang.annotation.*;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface SpringTransactional {
  int bulkSize() default 50;

  /**
   * Due to the nature of the Spring Transaction handling this timeout is in seconds.
   *
   * @return
   */
  int timeoutInSeconds() default Defaults.timeoutInSeconds;

  @UtilityClass
  class Defaults {
    static final int timeoutInSeconds = 30;

    public static DefaultTransactionDefinition create() {
      DefaultTransactionDefinition definition = new DefaultTransactionDefinition();

      definition.setTimeout(timeoutInSeconds);
      definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

      return definition;
    }

    public static TransactionDefinition with(@NonNull SpringTransactional transactional) {
      DefaultTransactionDefinition opts = create();

      int timeout = transactional.timeoutInSeconds();

      if (timeout > 0) {
        opts.setTimeout(timeout);
      }

      return opts;
    }
  }
}
