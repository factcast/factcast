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

import com.fasterxml.jackson.annotation.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.*;

/**
 * Defines a Specification of facts to match for a subscription.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(
    of = {
      "ns",
      "type",
      "mergedAggIds", // don't forget to change this one when removing the deprecated aggId
      "meta",
      "metaKeyExists",
      "filterScript"
    }) // in order to skip aggId field
@SuppressWarnings({"java:S1874", "java:S1123"})
public class FactSpec {

  @NonNull @JsonProperty private String ns;

  // instead of a setter
  public @NonNull FactSpec withNs(@NonNull String ns) {
    this.ns = ns;
    return this;
  }

  @JsonProperty private String type = null;

  @JsonProperty private int version = 0; // 0 means I don't care

  @JsonProperty
  private UUID aggId = null;

  @JsonProperty private final Set<UUID> aggIds = new LinkedHashSet<>();

  @JsonProperty private final Map<String, String> meta = new LinkedHashMap<>();

  /** expresses the mandatory existence or absence of a key. Needs stable order. */
  @JsonProperty private final Map<String, Boolean> metaKeyExists = new LinkedHashMap<>();

  @JsonProperty private FilterScript filterScript = null;

  /**
   * only to be used internally, will be removed again once aggId field is removed.
   *
   * @return
   */
  @Deprecated
  public Set<UUID> mergedAggIds() {
    Set<UUID> copy = new HashSet<>(aggIds);
    // merge the single aggId for compatibility with clients < 0.9
    if (aggId != null) copy.add(aggId);
    return Collections.unmodifiableSet(copy);
  }

  public FactSpec aggId(@NonNull UUID aggId, UUID... otherAggIds) {
    aggIds.add(aggId);
    if (otherAggIds != null) {
      aggIds.addAll(
          Arrays.stream(otherAggIds)
              .filter(Objects::nonNull)
              .collect(Collectors.toList())); // toSet would potentially flip the order
    }
    return this;
  }

  public FactSpec metaExists(@NonNull String k) {
    metaKeyExists.put(k, Boolean.TRUE);
    return this;
  }

  public FactSpec metaDoesNotExist(@NonNull String k) {
    metaKeyExists.put(k, Boolean.FALSE);
    return this;
  }

  public FactSpec meta(@NonNull String k, @NonNull String v) {
    meta.put(k, v);
    return this;
  }

  public static FactSpec ns(@NonNull String ns) {
    assertNotEmpty(ns);
    return new FactSpec(ns);
  }

  private static void assertNotEmpty(String ns) {
    if (ns.trim().isEmpty()) throw new IllegalArgumentException("Namespace must not be empty");
  }

  public FactSpec(@NonNull @JsonProperty("ns") String ns) {
    super();
    assertNotEmpty(ns);
    this.ns = ns;
  }

  public FilterScript filterScript() {
    if (filterScript != null) return filterScript;
    else return null;
  }

  @NonNull
  public FactSpec filterScript(FilterScript script) {
    this.filterScript = script;
    return this;
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
    FactSpec fs = FactSpec.ns(ns).type(type).version(version).filterScript(filterScript);
    fs.aggId = aggId;
    fs.aggIds.addAll(aggIds);
    fs.meta.putAll(meta);
    return fs;
  }

  public @NonNull String ns() {
    return this.ns;
  }

  public String type() {
    return this.type;
  }

  public int version() {
    return this.version;
  }

  public Set<UUID> aggIds() {
    return this.aggIds;
  }

  public Map<String, String> meta() {
    return this.meta;
  }

  public Map<String, Boolean> metaKeyExists() {
    return this.metaKeyExists;
  }

  /** overwrites type as there can be only one */
  @JsonProperty
  public FactSpec type(String type) {
    this.type = type;
    return this;
  }

  /** overwrites version as there can be only one */
  @JsonProperty
  public FactSpec version(int version) {
    this.version = version;
    return this;
  }

  public String toString() {
    return "FactSpec(ns="
        + this.ns()
        + ", type="
        + this.type()
        + ", version="
        + this.version()
        + ", aggId="
        + this.aggId
        + ", aggIds="
        + this.aggIds()
        + ", meta="
        + this.meta()
        + ", metaKeyExists="
        + this.metaKeyExists()
        + ", filterScript="
        + this.filterScript()
        + ")";
  }
}
