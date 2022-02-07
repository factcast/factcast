package org.factcast.factus.dynamodb.tx;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface DynamoTransactional {
  int bulkSize() default 50;
}
