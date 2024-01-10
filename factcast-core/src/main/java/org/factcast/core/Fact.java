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
package org.factcast.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.spec.FactSpecCoordinates;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.event.DefaultEventSerializer;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.EventSerializer;

/**
 * Defines a fact to be either published or consumed. Consists of two JSON Strings: jsonHeader and
 * jsonPayload. Also provides convenience getters for id,ns,type and aggId.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public interface Fact {

  @NonNull
  UUID id();

  @NonNull
  String ns();

  String type();

  int version();

  @NonNull
  Set<UUID> aggIds();

  @NonNull
  String jsonHeader();

  @NonNull
  FactHeader header();

  @NonNull
  String jsonPayload();

  /**
   * @param key
   * @return value as String or null
   * @deprecated use header.meta(String) instead
   */
  @Deprecated
  @Nullable
  String meta(@NonNull String key);

  /**
   * @return meta._ser
   * @throws IllegalStateException if serial information is missing
   * @deprecated use header.serial() instead.
   */
  @Deprecated
  default long serial() {
    return Optional.ofNullable(header().serial())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "'_ser' Meta attribute not found. Fact not yet published?"));
  }

  /**
   * beware, might return null
   *
   * @return timestamp in milliseconds of publishing, or null if this information is not there
   *     (historic events)
   * @deprecated use header.timestamp() instead.
   */
  @Nullable
  @Deprecated
  default Long timestamp() {
    return header().timestamp();
  }

  // hint to where to get the default from
  static Fact of(@NonNull String jsonHeader, @NonNull String jsonPayload) {
    return DefaultFact.of(jsonHeader, jsonPayload);
  }

  static Fact of(@NonNull JsonNode jsonHeader, @NonNull JsonNode jsonPayload) {
    return DefaultFact.of(jsonHeader.toString(), jsonPayload.toString());
  }

  // create a fact from the event, especially useful for testing purposes

  static FactFromEventBuilder buildFrom(@NonNull EventObject event) {
    FactFromEventBuilder b = new FactFromEventBuilder(event);
    // from event
    FactSpecCoordinates coords = FactSpecCoordinates.from(event.getClass());
    b.type(coords.type()).ns(coords.ns());

    int v = coords.version();
    if (v > 0) b.version(v);
    // defaults
    b.serial(1).id(UUID.randomUUID());

    event.aggregateIds().forEach(b::aggId);
    event.additionalMetaMap().forEach(b::meta);
    return b;
  }

  default boolean before(Fact other) {
    return serial() < other.serial();
  }

  @RequiredArgsConstructor
  class FactFromEventBuilder {
    @NonNull private final EventObject event;
    private final Builder builder = new Builder();

    private EventSerializer ser = new DefaultEventSerializer(FactCastJson.mapper());

    public FactFromEventBuilder using(@NonNull EventSerializer ser) {
      this.ser = ser;
      return this;
    }

    public FactFromEventBuilder aggId(@NonNull UUID aggId) {
      builder.aggId(aggId);
      return this;
    }

    public FactFromEventBuilder ns(@NonNull String ns) {
      builder.ns(ns);
      return this;
    }

    public FactFromEventBuilder id(@NonNull UUID id) {
      builder.id(id);
      return this;
    }

    public FactFromEventBuilder type(@NonNull String type) {
      builder.type(type);
      return this;
    }

    public FactFromEventBuilder serial(long id) {
      builder.serial(id);
      return this;
    }

    public FactFromEventBuilder version(int version) {
      builder.version(version);
      return this;
    }

    public FactFromEventBuilder meta(@NonNull String key, String value) {
      builder.meta(key, value);
      return this;
    }

    public Fact build() {
      return builder.build(ser.serialize(event));
    }
  }

  static Fact.Builder builder() {
    return new Builder();
  }

  class Builder {

    final FactHeader header = new FactHeader().id(UUID.randomUUID()).ns("default");

    public Builder aggId(@NonNull UUID aggId) {
      header.aggIds().add(aggId);
      return this;
    }

    public Builder ns(@NonNull String ns) {
      if (ns.trim().isEmpty()) {
        throw new IllegalArgumentException("Namespace must not be empty");
      }
      header.ns(ns);
      return this;
    }

    public Builder id(@NonNull UUID id) {
      header.id(id);
      return this;
    }

    public Builder type(@NonNull String type) {
      if (type.trim().isEmpty()) {
        throw new IllegalArgumentException("type must not be empty");
      }

      header.type(type);
      return this;
    }

    public Builder serial(long id) {
      meta("_ser", String.valueOf(id));
      return this;
    }

    public Builder version(int version) {
      if (version < 1) {
        throw new IllegalArgumentException("version must be >=1");
      }
      header.version(version);
      return this;
    }

    public Builder meta(@NonNull String key, String value) {
      header.meta().put(key, value);
      return this;
    }

    public Fact buildWithoutPayload() {
      return build(null);
    }

    public Fact build(String payload) {
      String pl = payload;
      if (payload == null || payload.trim().isEmpty()) {
        pl = "{}";
      }
      return new DefaultFact(header, pl);
    }
  }
}
