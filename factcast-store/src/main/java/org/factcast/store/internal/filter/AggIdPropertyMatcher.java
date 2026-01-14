/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.internal.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import java.util.*;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.factcast.core.spec.*;
import org.factcast.store.internal.PgFact;

/**
 * Matches facts against specifications regarding the existence of String:UUID pairs in the payload
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public final class AggIdPropertyMatcher implements PGFactMatcher {

  final Map<String, UUID> aggIdProperties;

  private AggIdPropertyMatcher(@NonNull FactSpec spec) {
    aggIdProperties = spec.aggIdProperties();
  }

  public static @Nullable AggIdPropertyMatcher matches(FactSpec spec) {
    if (spec.aggIdProperties().isEmpty()) {
      return null;
    } else {
      return new AggIdPropertyMatcher(spec);
    }
  }

  @Override
  public boolean test(PgFact t) {
    return aggIdPropertiesMatch(t);
  }

  @VisibleForTesting
  boolean aggIdPropertiesMatch(PgFact t) {
    JsonNode payloadRoot = t.jsonPayloadParsed();

    for (Map.Entry<String, UUID> entry : aggIdProperties.entrySet()) {
      String k = entry.getKey();
      UUID v = entry.getValue();
      String[] nodes = path(k);
      JsonNode payload = payloadRoot;
      // we decent into the tree
      for (String node : nodes) {
        payload = payload.path(node);
        if (payload.isMissingNode()) {
          return false; // as early as possible
        }
      }
      // string comparison is twice as fast, compared to parsing a UUID
      if (!v.toString().equals(payload.toString())) {
        return false;
      }
    }

    return true;
  }

  private String[] path(String k) {
    return k.split("\\.");
  }

  /**
   * extract fieldname from propertyPath expression
   *
   * @param k
   * @return
   */
  @VisibleForTesting
  static String fieldName(@NonNull String k) {
    if (!k.contains(".")) {
      return k;
    } else {
      return StringUtils.substringAfterLast(k, '.');
    }
  }
}
