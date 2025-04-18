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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface SpringTransactional {

  int DEFAULT_BULK_SIZE = 1000;

  int bulkSize() default DEFAULT_BULK_SIZE;

  int propagationBehavior() default TransactionDefinition.PROPAGATION_REQUIRED;

  int isolationLevel() default TransactionDefinition.ISOLATION_DEFAULT;

  int timeoutInSeconds() default Defaults.TIMEOUT_IN_SECONDS;

  boolean readOnly() default false;

  class Defaults {
    static final int TIMEOUT_IN_SECONDS = 30;

    public static DefaultTransactionDefinition create() {
      DefaultTransactionDefinition opts = new DefaultTransactionDefinition();

      opts.setTimeout(TIMEOUT_IN_SECONDS);

      return opts;
    }

    public static TransactionDefinition with(@NonNull SpringTransactional transactional) {
      DefaultTransactionDefinition opts = create();

      opts.setTimeout(transactional.timeoutInSeconds());
      opts.setIsolationLevel(transactional.isolationLevel());
      opts.setReadOnly(transactional.readOnly());
      opts.setPropagationBehavior(transactional.propagationBehavior());

      return opts;
    }

    private Defaults() {}
  }
}
