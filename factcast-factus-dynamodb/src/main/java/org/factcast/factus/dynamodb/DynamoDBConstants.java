package org.factcast.factus.dynamodb;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DynamoDBConstants {
  public static final String LOCK_TABLE = "factus-dynamodb";
  public static final long DEFAULT_TIMEOUT = 3000;
}
