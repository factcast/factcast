package org.factcast.factus.spring.tx;

import java.lang.annotation.*;
import lombok.NonNull;
import lombok.val;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface SpringTransactional {
  int size() default 50;

  int timeout() default Defaults.timeout;

  class Defaults {
    static final int timeout = 30;

    public static DefaultTransactionDefinition create() {
      val definition = new DefaultTransactionDefinition();

      definition.setTimeout(timeout);
      definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

      return definition;
    }

    public static TransactionDefinition with(@NonNull SpringTransactional transactional) {
      val opts = create();

      int timeout = transactional.timeout();

      if (timeout > 0) {
        opts.setTimeout(timeout);
      }

      return opts;
    }
  }
}
