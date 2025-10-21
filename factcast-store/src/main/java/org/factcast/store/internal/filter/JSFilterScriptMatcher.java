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
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;
import lombok.*;
import org.factcast.core.spec.*;
import org.factcast.store.internal.PgFact;
import org.factcast.store.internal.script.*;

/**
 * Matches facts against specifications using the contained filterScripts
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public final class JSFilterScriptMatcher implements PGFactMatcher {

  final JSEngine scriptEngine;

  private JSFilterScriptMatcher(@NonNull FactSpec spec, @NonNull JSEngineFactory ef) {
    FilterScript script = spec.filterScript();
    Preconditions.checkNotNull(script);
    Preconditions.checkNotNull(script.source());
    scriptEngine = getEngine(script, ef);
  }

  @Override
  public boolean test(PgFact t) {
    return scriptMatch(t);
  }

  @SneakyThrows
  @Generated
  boolean scriptMatch(PgFact t) {
    JsonNode headerNode = t.jsonHeaderParsed();
    JsonNode payloadNode = t.jsonPayloadParsed();
    return (Boolean)
        scriptEngine.invoke(
            "test", JSArgument.byValue(headerNode), JSArgument.byValue(payloadNode));
  }

  @SneakyThrows
  @Generated
  private static synchronized JSEngine getEngine(
      FilterScript filterScript, @NonNull JSEngineFactory ef) {

    return switch (filterScript.languageIdentifier()) {
        //  currently only supports language js
      case "js" -> ef.getOrCreateFor("var test=" + filterScript.source());
      default ->
          throw new IllegalArgumentException(
              "Unsupported Script language: " + filterScript.languageIdentifier());
    };
  }

  public static @Nullable JSFilterScriptMatcher matches(
      @NonNull FactSpec spec, @NonNull JSEngineFactory ef) {
    FilterScript script = spec.filterScript();
    if (script != null && !script.source().isBlank()) {
      return new JSFilterScriptMatcher(spec, ef);
    }

    // otherwise
    return null;
  }
}
