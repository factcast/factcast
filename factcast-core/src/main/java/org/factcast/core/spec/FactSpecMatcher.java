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
package org.factcast.core.spec;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Generated;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.Fact;
import org.factcast.script.engine.Engine;
import org.factcast.script.engine.graaljs.GraalJSEngineCache;

/**
 * Matches facts against specifications.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public final class FactSpecMatcher implements Predicate<Fact> {

  @NonNull final String ns;

  final Integer version;

  final String type;

  final UUID aggId;

  final Map<String, String> meta;

  final FilterScript script;

  final Engine scriptEngine;

  private static final GraalJSEngineCache scriptEngineCache = new GraalJSEngineCache();

  public FactSpecMatcher(@NonNull FactSpec spec) {
    // opt: prevent method calls by prefetching to final fields.
    // yes, they might be inlined at some point, but making decisions based
    // on final fields should help.
    //
    // this Predicate is pretty performance critical
    ns = spec.ns();
    type = spec.type();
    version = spec.version();
    aggId = spec.aggId();
    meta = spec.meta();
    script = spec.filterScript();
    scriptEngine = getEngine(script);
  }

  @Override
  public boolean test(Fact t) {
    boolean match = nsMatch(t);
    match = match && typeMatch(t);
    match = match && versionMatch(t);
    match = match && aggIdMatch(t);
    match = match && metaMatch(t);
    match = match && scriptMatch(t);
    return match;
  }

  protected boolean metaMatch(Fact t) {
    if ((meta.isEmpty())) {
      return true;
    }
    return meta.entrySet().stream().allMatch(e -> e.getValue().equals(t.meta(e.getKey())));
  }

  protected boolean nsMatch(Fact t) {
    return ns.equals(t.ns());
  }

  protected boolean typeMatch(Fact t) {
    if (type == null) {
      return true;
    }
    String otherType = t.type();
    return type.equals(otherType);
  }

  protected boolean versionMatch(Fact t) {
    if (version == 0) {
      return true;
    }
    Integer otherVersion = t.version();
    return version.equals(otherVersion);
  }

  protected boolean aggIdMatch(Fact t) {
    if (aggId == null) {
      return true;
    }
    return t.aggIds().contains(aggId);
  }

  @SneakyThrows
  @Generated
  protected boolean scriptMatch(Fact t) {
    if (script == null) {
      return true;
    }
    return (Boolean) scriptEngine.eval("test(" + t.jsonHeader() + "," + t.jsonPayload() + ")");
  }

  @SneakyThrows
  @Generated
  private static synchronized Engine getEngine(FilterScript filterScript) {
    if (filterScript == null) {
      return null;
    }

    // TODO: currently only supports language js:
    if ("js".equals(filterScript.languageIdentifier())) {

      Engine cachedEngine = scriptEngineCache.get("var test=" + filterScript.source());
      return cachedEngine;
    } else {
      // TODO really?
      throw new IllegalArgumentException(
          "Unsupported Script language: " + filterScript.languageIdentifier());
    }
  }

  public static Predicate<Fact> matchesAnyOf(@NonNull List<FactSpec> spec) {
    List<FactSpecMatcher> matchers =
        spec.stream().map(FactSpecMatcher::new).collect(Collectors.toList());
    return f -> matchers.stream().anyMatch(p -> p.test(f));
  }

  public static Predicate<Fact> matches(@NonNull FactSpec spec) {
    return new FactSpecMatcher(spec);
  }
}
