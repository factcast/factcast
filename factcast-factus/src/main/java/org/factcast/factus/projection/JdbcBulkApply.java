package org.factcast.factus.projection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
// TODO move
public @interface JdbcBulkApply {

  /**
   * During catchup phase, try to apply up to batchSize number of events before committing. if set
   * to 0 defaults to 50
   */
  int size() default 50;

  /** commit prematurely if timeoutInMs is exceeded (reduces batch size), defaults to 20seconds */
  long timeoutInMs() default 20000;
}
