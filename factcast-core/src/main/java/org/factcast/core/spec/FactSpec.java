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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Defines a Specification of facts to match for a subscription.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode
@ToString
@SuppressWarnings({"java:S1874", "java:S1123"})
public class FactSpec {

  @Getter @NonNull @JsonProperty private String ns;

  @Getter @JsonProperty private String type;

  @Getter @JsonProperty private int version; // 0 means I don't care

  @JsonProperty private final Set<UUID> aggIds = new LinkedHashSet<>();

  @JsonProperty private final Map<String, String> meta = new LinkedHashMap<>();

  @JsonProperty private final SortedMap<String, UUID> aggIdProperties = new TreeMap<>();

  /** expresses the mandatory existence or absence of a key. Needs stable order. */
  @JsonProperty private final Map<String, Boolean> metaKeyExists = new LinkedHashMap<>();

  @Getter @JsonProperty private FilterScript filterScript;

  public FactSpec aggIdProperty(@NonNull String propertyPath, @NonNull UUID aggId) {
    aggIds.add(aggId);
    aggIdProperties.put(propertyPath, aggId);
    return this;
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
    if (ns.trim().isEmpty()) {
      throw new IllegalArgumentException("Namespace must not be empty");
    }
  }

  public FactSpec(@NonNull @JsonProperty("ns") String ns) {
    super();
    assertNotEmpty(ns);
    this.ns = ns;
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
    fs.aggIds.addAll(aggIds);
    fs.aggIdProperties.putAll(aggIdProperties);
    fs.metaKeyExists.putAll(metaKeyExists);
    fs.meta.putAll(meta);
    return fs;
  }

  /**
   * used to subscribe to changes for cache invalidation
   *
   * @return copy with aggIds and aggIdProperties removed
   */
  public FactSpec withoutAggIds() {
    FactSpec fs = FactSpec.ns(ns).type(type).version(version).filterScript(filterScript);
    fs.metaKeyExists.putAll(metaKeyExists);
    fs.meta.putAll(meta);
    return fs;
  }

  public FactSpec withNs(String newNs) {
    FactSpec fs = FactSpec.ns(newNs).type(type).version(version).filterScript(filterScript);
    fs.aggIds.addAll(aggIds);
    fs.aggIdProperties.putAll(aggIdProperties);
    fs.meta.putAll(meta);
    fs.metaKeyExists.putAll(metaKeyExists);
    return fs;
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

  public Map<String, String> meta() {
    return Collections.unmodifiableMap(meta);
  }

  public Set<UUID> aggIds() {
    return Collections.unmodifiableSet(aggIds);
  }

  public SortedMap<String, UUID> aggIdProperties() {
    return Collections.unmodifiableSortedMap(aggIdProperties);
  }
}
