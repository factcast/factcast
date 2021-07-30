package org.factcast.factus.spring.tx;

import lombok.NonNull;
import lombok.val;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface SpringTransactional {
  int size() default 50;

  /**
   * Due to the nature of the Spring Transaction handling this timeout is in seconds.
   *
   * @return
   */
  int timeoutInSeconds() default Defaults.timeoutInSeconds;

  class Defaults {
    static final int timeoutInSeconds = 30;

    public static DefaultTransactionDefinition create() {
      val definition = new DefaultTransactionDefinition();

      definition.setTimeout(timeoutInSeconds);
      definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

      return definition;
    }

    public static TransactionDefinition with(@NonNull SpringTransactional transactional) {
      val opts = create();

      int timeout = transactional.timeoutInSeconds();

      if (timeout > 0) {
        opts.setTimeout(timeout);
      }

      return opts;
    }
  }
}
