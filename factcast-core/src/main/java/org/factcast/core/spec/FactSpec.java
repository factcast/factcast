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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NonNull;

/**
 * Defines a Specification of facts to match for a subscription.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FactSpec {

  @NonNull @JsonProperty final String ns;

  // type & aggId should probably be sets?
  @JsonProperty String type = null;

  @JsonProperty int version = 0; // 0 means I don't care

  @JsonProperty UUID aggId = null;

  @NonNull @JsonProperty final Map<String, String> meta = new HashMap<>();

  @Deprecated @JsonProperty String jsFilterScript = null;

  @JsonProperty FilterScript filterScript = null;

  public FactSpec meta(@NonNull String k, @NonNull String v) {
    meta.put(k, v);
    return this;
  }

  public static FactSpec ns(@NonNull String ns) {
    return new FactSpec(ns);
  }

  public FactSpec(@NonNull @JsonProperty("ns") String ns) {
    super();
    this.ns = ns;
  }

  public FilterScript filterScript() {
    if (filterScript != null) return filterScript;
    else if (jsFilterScript != null) return new FilterScript("js", jsFilterScript);
    else return null;
  }

  @NonNull
  public FactSpec filterScript(FilterScript script) {
    if (script != null) {
      this.filterScript = script;
      if ("js".equals(script.languageIdentifier())) jsFilterScript = script.source();
    } else {
      filterScript = null;
      jsFilterScript = null;
    }

    return this;
  }

  @NonNull
  public FactSpec jsFilterScript(String script) {
    if (script != null) filterScript(new FilterScript("js", script));
    else filterScript(null);

    return this;
  }

  public String jsFilterScript() {
    if (filterScript != null && "js".equals(filterScript.languageIdentifier()))
      return filterScript.source();
    else if (filterScript == null && jsFilterScript != null) return jsFilterScript;
    else return null;
  }

  @NonNull
  public static <T> FactSpec from(@NonNull Class<T> clazz) {
    return from(FactSpecCoordinates.from(clazz));
  }

  private static FactSpec from(FactSpecCoordinates from) {
    return FactSpec.ns(from.ns()).type(from.type()).version(from.version());
  }

  /** convenience method */
  public static List<FactSpec> from(@NonNull List<Class<?>> clazz) {
    return clazz.stream().filter(Objects::nonNull).map(FactSpec::from).collect(Collectors.toList());
  }

  /** convenience method */
  public static List<FactSpec> from(Class<?>... clazz) {
    return from(Arrays.asList(clazz));
  }

  public FactSpec copy() {
    FactSpec fs =
        FactSpec.ns(ns).type(type).version(version).aggId(aggId).filterScript(filterScript);
    fs.meta.putAll(meta);
    return fs;
  }
}
