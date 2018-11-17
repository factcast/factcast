/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.store.pgsql.internal;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;

/**
 * PG Specific implementation of a Fact.
 *
 * This class is necessary in order to delay parsing of the header until
 * necessary (when accessing meta-data)
 *
 * @author uwe.schaefer@mercateo.com
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(of = { "id" })
public class PGFact implements Fact {

    @Getter
    @NonNull
    final UUID id;

    @Getter
    @NonNull
    final String ns;

    @Getter
    final String type;

    @Getter
    final Set<UUID> aggIds;

    @Getter
    @NonNull
    final String jsonHeader;

    @Getter
    @NonNull
    final String jsonPayload;

    @JsonProperty
    Map<String, String> meta = null;

    @Override
    public String meta(String key) {
        if (meta == null) {
            meta = deser();
        }
        return meta.get(key);
    }

    @SneakyThrows
    private Map<String, String> deser() {
        Meta deserializedMeta = FactCastJson.readValue(Meta.class, jsonHeader);
        return deserializedMeta.meta;
    }

    // just picks the MetaData from the Header (as we know the rest already
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Meta {

        @JsonProperty
        final Map<String, String> meta = new HashMap<>();
    }

    @SneakyThrows
    public static Fact from(ResultSet resultSet) {
        String id = resultSet.getString(PGConstants.ALIAS_ID);
        String aggId = resultSet.getString(PGConstants.ALIAS_AGGID);
        String type = resultSet.getString(PGConstants.ALIAS_TYPE);
        String ns = resultSet.getString(PGConstants.ALIAS_NS);
        String jsonHeader = resultSet.getString(PGConstants.COLUMN_HEADER);
        String jsonPayload = resultSet.getString(PGConstants.COLUMN_PAYLOAD);
        return new PGFact(UUID.fromString(id), ns, type, toUUIDArray(aggId), jsonHeader,
                jsonPayload);
    }

    @VisibleForTesting
    static Set<UUID> toUUIDArray(String aggIdArrayAsString) {
        Set<UUID> set = new LinkedHashSet<>();
        if (aggIdArrayAsString != null && aggIdArrayAsString.trim().length() > 2) {
            UUID[] readValue = FactCastJson.readValue(UUID[].class, aggIdArrayAsString);
            if (readValue != null) {
                set.addAll(Arrays.asList(readValue));
            }
        }
        return set;
    }
}
