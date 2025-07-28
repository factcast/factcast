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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import java.util.function.Predicate;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.factcast.core.Fact;
import org.factcast.core.spec.*;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.internal.script.*;

/**
 * Matches facts against specifications.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public final class FactSpecMatcher implements Predicate<Fact> {

  public boolean UNSAFE_STRING_PREMATCHING = true;
  @NonNull final String ns;

  final Integer version;

  final String type;

  final Set<UUID> aggIds;

  final Map<String, String> meta;

  final Map<String, UUID> aggIdProperties;

  final FilterScript script;

  final JSEngine scriptEngine;

  final Map<String, Boolean> metaKeyExists;

  public FactSpecMatcher(@NonNull FactSpec spec, @NonNull JSEngineFactory ef) {
    // opt: prevent method calls by prefetching to final fields.
    // yes, they might be inlined at some point, but making decisions based
    // on final fields should help.
    //
    // this Predicate is pretty performance critical
    ns = spec.ns();
    type = spec.type();
    version = spec.version();
    aggIds = spec.aggIds();
    aggIdProperties = spec.aggIdProperties();
    meta = spec.meta();
    metaKeyExists = spec.metaKeyExists();
    script = spec.filterScript();
    scriptEngine = getEngine(script, ef);
  }

  @Override
  public boolean test(Fact t) {
    boolean match = nsMatch(t);
    match = match && typeMatch(t);
    match = match && versionMatch(t);
    match = match && aggIdMatch(t);
    match = match && aggIdPropertiesMatch(t);
    match = match && metaMatch(t);
    match = match && metaKeyExistsMatch(t);
    match = match && scriptMatch(t);

    return match;
  }

  @VisibleForTesting
  boolean aggIdPropertiesMatch(Fact t) {

    for (Map.Entry<String, UUID> entry : aggIdProperties.entrySet()) {
      String k = entry.getKey();
      UUID v = entry.getValue();
      String uuidAsString = v.toString();
      String fieldName = fieldName(k);
      // as we know the fact json being normalized (comes from the database), we can try to
      // get away with a cheap string contains to detect non matching ones quicker.
      String extectedString = String.format("\"%s\":\"%s\"", fieldName, uuidAsString);
      if (UNSAFE_STRING_PREMATCHING)
        if (!t.jsonPayload().contains(extectedString)) {
          return false;
        }

      // we'll have to have a closer look to make sure

      JsonNode payload = null;
      try {
        payload = FactCastJson.readTree(t.jsonPayload());
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
      String[] nodes = path(k);
      for (String node : nodes) {
        payload = payload.path(node);
        if (payload.isMissingNode()) {
          return false; // as early as possible
        }
      }
      return v.toString().equals(payload.asText());
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
    if (!k.contains(".")) return k;
    else return StringUtils.substringAfterLast(k, '.');
  }

  boolean metaMatch(Fact t) {
    if (meta.isEmpty()) {
      return true;
    }
    return meta.entrySet().stream()
        .allMatch(e -> t.header().meta().getAll(e.getKey()).contains(e.getValue()));
  }

  boolean metaKeyExistsMatch(Fact t) {
    if (metaKeyExists.isEmpty()) {
      return true;
    }
    return metaKeyExists.entrySet().stream()
        .allMatch(
            e -> {
              boolean mustExist = Objects.requireNonNull(e.getValue());
              String metaValue = t.header().meta().getFirst(e.getKey());
              return (mustExist && metaValue != null) || (!mustExist && metaValue == null);
            });
  }

  boolean nsMatch(Fact t) {
    return ns.equals(t.ns()) || ns.equals("*");
  }

  boolean typeMatch(Fact t) {
    if (type == null) {
      return true;
    }
    String otherType = t.type();
    return type.equals(otherType);
  }

  boolean versionMatch(Fact t) {
    if (version == 0) {
      return true;
    }
    Integer otherVersion = t.version();
    return version.equals(otherVersion);
  }

  boolean aggIdMatch(Fact t) {
    if (aggIds == null || aggIds.isEmpty()) {
      return true;
    }
    return t.aggIds().containsAll(aggIds);
  }

  @SneakyThrows
  @Generated
  boolean scriptMatch(Fact t) {
    if (script == null) {
      return true;
    }
    JsonNode headerNode = FactCastJson.readTree(t.jsonHeader());
    JsonNode payloadNode = FactCastJson.readTree(t.jsonPayload());
    return (Boolean)
        scriptEngine.invoke(
            "test", JSArgument.byValue(headerNode), JSArgument.byValue(payloadNode));
  }

  @SneakyThrows
  @Generated
  private static synchronized JSEngine getEngine(
      FilterScript filterScript, @NonNull JSEngineFactory ef) {
    if (filterScript == null) {
      return null;
    }

    // TODO: currently only supports language js:
    if ("js".equals(filterScript.languageIdentifier())) {

      return ef.getOrCreateFor("var test=" + filterScript.source());
    } else {
      throw new IllegalArgumentException(
          "Unsupported Script language: " + filterScript.languageIdentifier());
    }
  }

  public static Predicate<Fact> matchesAnyOf(
      @NonNull List<FactSpec> spec, @NonNull JSEngineFactory ef) {
    List<FactSpecMatcher> matchers = spec.stream().map(s -> new FactSpecMatcher(s, ef)).toList();
    return f -> matchers.stream().anyMatch(p -> p.test(f));
  }

  public static Predicate<Fact> matches(@NonNull FactSpec spec, @NonNull JSEngineFactory ef) {
    return new FactSpecMatcher(spec, ef);
  }
}
