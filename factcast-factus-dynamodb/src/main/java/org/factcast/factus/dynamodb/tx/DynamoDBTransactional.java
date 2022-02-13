package org.factcast.factus.dynamodb.tx;

import java.lang.annotation.*;
import org.factcast.factus.dynamodb.DynamoDBConstants;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface DynamoDBTransactional {
  int bulkSize() default 50;

  long timeout() default DynamoDBConstants.DEFAULT_TIMEOUT;
}
