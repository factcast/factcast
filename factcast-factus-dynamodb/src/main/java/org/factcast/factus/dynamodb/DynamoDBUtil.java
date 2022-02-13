package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DynamoDBUtil {
  private static final DateTimeFormatter formatter =
      new DateTimeFormatterBuilder().parseCaseInsensitive().appendInstant(3).toFormatter();

  public static AttributeValue attrS(String lockUntilIso) {
    return new AttributeValue().withS(lockUntilIso);
  }

  // TODO move
  static String toIsoString(@NonNull Instant instant) {
    OffsetDateTime utc = instant.atOffset(ZoneOffset.UTC);
    return formatter.format(utc);
  }
}
