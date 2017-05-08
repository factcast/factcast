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
 *
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(of = { "id" })
class PGFact implements Fact {

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
