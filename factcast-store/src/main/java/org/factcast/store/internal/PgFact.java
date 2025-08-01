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
package org.factcast.store.internal;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import lombok.*;
import org.factcast.core.*;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.event.MetaMap;

/**
 * PG Specific implementation of a Fact.
 *
 * <p>This class is necessary in order to delay parsing of the header until necessary (when
 * accessing meta-data)
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(of = {"id"})
public class PgFact implements Fact {

  @Getter @NonNull final UUID id;

  @Getter @NonNull final String ns;

  @Getter final String type;

  @Getter final int version;

  @Getter final Set<UUID> aggIds;

  @Getter @NonNull final String jsonHeader;

  @Getter @NonNull final String jsonPayload;

  @JsonProperty MetaMap meta;

  public static PgFact of(@NonNull JsonNode header, @NonNull JsonNode transformedPayload) {
    // horribly wasteful here: TODO
    Fact fact = Fact.of(header.toString(), transformedPayload.toString());
    PgFact pgFact =
        new PgFact(
            fact.id(),
            fact.ns(),
            fact.type(),
            fact.version(),
            fact.aggIds(),
            fact.jsonHeader(),
            fact.jsonPayload());
    pgFact.parsedHeader.set(header);
    pgFact.parsedPayload.set(transformedPayload);
    return pgFact;
  }

  public static PgFact of(@NonNull String header, @NonNull String transformedPayload) {
    // bit wasteful here: TODO
    Fact fact = Fact.of(header, transformedPayload);
    return new PgFact(
        fact.id(),
        fact.ns(),
        fact.type(),
        fact.version(),
        fact.aggIds(),
        fact.jsonHeader(),
        fact.jsonPayload());
  }

  @VisibleForTesting
  public static PgFact from(@NonNull Fact f) {
    return of(f.jsonHeader(), f.jsonPayload());
  }

  /**
   * @param key
   * @return value as String or null
   * @deprecated use header.meta(String) instead
   */
  @Deprecated(since = "0.10", forRemoval = true)
  @Nullable
  public String meta(String key) {
    if (meta == null) {
      meta = deserializeMeta();
    }
    return meta.getFirst(key);
  }

  @SuppressWarnings("java:S2065")
  @JsonIgnore
  private transient FactHeader header;

  @Override
  public @NonNull FactHeader header() {
    if (header == null) {
      // prefer preparsed header if avail
      if (parsedHeader.get() != null) {
        header = FactCastJson.readValue(FactHeader.class, parsedHeader.get());
      } else {
        header = FactCastJson.readValue(FactHeader.class, jsonHeader);
      }
    }
    return header;
  }

  private MetaMap deserializeMeta() {
    return header().meta();
  }

  public static PgFact from(ResultSet resultSet) throws SQLException {
    String id = resultSet.getString(PgConstants.ALIAS_ID);
    String aggId = resultSet.getString(PgConstants.ALIAS_AGGID);
    String type = resultSet.getString(PgConstants.ALIAS_TYPE);
    String ns = resultSet.getString(PgConstants.ALIAS_NS);
    String jsonHeader = resultSet.getString(PgConstants.COLUMN_HEADER);
    String jsonPayload = resultSet.getString(PgConstants.COLUMN_PAYLOAD);
    int version = resultSet.getInt(PgConstants.COLUMN_VERSION);
    return new PgFact(
        UUID.fromString(id), ns, type, version, toUUIDSet(aggId), jsonHeader, jsonPayload);
  }

  @VisibleForTesting
  static Set<UUID> toUUIDSet(String aggIdArrayAsString) {
    if (aggIdArrayAsString != null && aggIdArrayAsString.trim().length() > 2) {
      UUID[] readValue = FactCastJson.readValue(UUID[].class, aggIdArrayAsString);
      if (readValue != null) {
        return Set.of(readValue);
      }
    }
    return Collections.emptySet();
  }

  @SuppressWarnings("java:S2065")
  @JsonIgnore
  private transient AtomicReference<JsonNode> parsedPayload = new AtomicReference<>();

  @SuppressWarnings("java:S2065")
  @JsonIgnore
  private transient AtomicReference<JsonNode> parsedHeader = new AtomicReference<>();

  @SneakyThrows
  public @NonNull JsonNode jsonPayloadParsed() {
    JsonNode p = parsedPayload.get();
    if (p == null) {
      p = FactCastJson.readTree(jsonPayload);
      parsedPayload.set(p);
    }
    return p;
  }

  @SneakyThrows
  public @NonNull JsonNode jsonHeaderParsed() {
    JsonNode p = parsedHeader.get();
    if (p == null) {
      p = FactCastJson.readTree(jsonHeader);
      parsedHeader.set(p);
    }
    return p;
  }
}
