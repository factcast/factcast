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
