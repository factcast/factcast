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

import com.google.common.base.Preconditions;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.*;
import org.factcast.core.spec.*;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.internal.PgFact;
import org.factcast.store.internal.script.*;
import org.factcast.store.internal.script.graaljs.NashornCompatContextBuilder;
import org.factcast.store.registry.transformation.chains.*;
import org.graalvm.polyglot.*;

/**
 * Matches facts against specifications using the contained filterScripts
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public final class JSFilterScriptMatcher implements PGFactMatcher {

  private static final Engine engine = Engine.newBuilder("js").build();

  interface BooleanFunction {
    boolean test(Map<String, Object> header, Map<String, Object> payload);
  }

  private final Source source;

  private JSFilterScriptMatcher(@NonNull FactSpec spec) {
    FilterScript filterScript = spec.filterScript();
    // filterscript might be named like "function x(header,payload){...}" so that
    // we need to convert it into an anonymous function
    String finalScript = "function (h,p) { var fn=" + filterScript.source() + "; return fn(h,p); }";
    this.source = Source.create(filterScript.languageIdentifier(), finalScript);
    Preconditions.checkNotNull(source);
  }

  @Override
  public boolean test(PgFact t) {
    return scriptMatch(t);
  }

  @SneakyThrows
  @Generated
  boolean scriptMatch(PgFact t) {
    Map<String, Object> header = FactCastJson.readValue(Map.class, t.jsonHeader());
    Map<String, Object> payload = FactCastJson.readValue(Map.class, t.jsonPayload());

    try (Context ctx = NashornCompatContextBuilder.CTX.engine(engine).build()) {
      return ctx.eval(source).as(BooleanFunction.class).test(header, payload);
    }
  }

  public static @Nullable JSFilterScriptMatcher matches(@NonNull FactSpec spec) {
    FilterScript script = spec.filterScript();
    if (script != null && !script.source().isBlank()) {
      return new JSFilterScriptMatcher(spec);
    }

    // otherwise
    return null;
  }
}
