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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import java.sql.*;
import java.util.*;
import javax.annotation.Nullable;
import lombok.*;
import org.factcast.core.*;
import org.factcast.core.util.FactCastJson;

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

  @JsonProperty FactMeta meta = null;

  /**
   * @param key
   * @return value as String or null
   * @deprecated use header.meta(String) instead
   */
  @Deprecated
  @Nullable
  public String meta(String key) {
    if (meta == null) {
      meta = deserializeMeta();
    }
    return meta.getFirst(key);
  }

  private transient FactHeader header;

  @Override
  public @NonNull FactHeader header() {
    if (header == null) {
      header = FactCastJson.readValue(FactHeader.class, jsonHeader);
    }
    return header;
  }

  private FactMeta deserializeMeta() {
    return header().meta();
  }

  public static Fact from(ResultSet resultSet) throws SQLException {
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
}
