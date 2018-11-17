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
package org.factcast.store.pgsql.internal.query;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.internal.PGConstants;
import org.springframework.jdbc.core.PreparedStatementSetter;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides {@link PreparedStatementSetter} and the corresponding SQL from a
 * list of {@link FactSpec}s.
 *
 * @author uwe.schaefer@mercateo.com
 */
@Slf4j
public class PGQueryBuilder {

    final boolean selectIdOnly;

    @NonNull
    final SubscriptionRequestTO req;

    public PGQueryBuilder(@NonNull SubscriptionRequestTO request) {
        this.req = request;
        selectIdOnly = request.idOnly() && !request.hasAnyScriptFilters();
    }

    public PreparedStatementSetter createStatementSetter(AtomicLong serial) {
        return p -> {
            // TODO vulnerable of json injection attack
            int count = 0;
            for (FactSpec spec : req.specs()) {
                p.setString(++count, "{\"ns\": \"" + spec.ns() + "\" }");
                String type = spec.type();
                if (type != null) {
                    p.setString(++count, "{\"type\": \"" + type + "\" }");
                }
                UUID agg = spec.aggId();
                if (agg != null) {
                    p.setString(++count, "{\"aggIds\": [\"" + agg + "\"]}");
                }
                Map<String, String> meta = spec.meta();
                for (Entry<String, String> e : meta.entrySet()) {
                    p.setString(++count, "{\"meta\":{\"" + e.getKey() + "\":\"" + e.getValue()
                            + "\" }}");
                }
            }
            p.setLong(++count, serial.get());
        };
    }

    private String createWhereClause() {
        List<String> predicates = new LinkedList<>();
        req.specs().forEach(spec -> {
            StringBuilder sb = new StringBuilder();
            sb.append("( ");
            sb.append(PGConstants.COLUMN_HEADER).append(" @> ?::jsonb ");
            String type = spec.type();
            if (type != null) {
                sb.append("AND ").append(PGConstants.COLUMN_HEADER).append(" @> ?::jsonb ");
            }
            UUID agg = spec.aggId();
            if (agg != null) {
                sb.append("AND ").append(PGConstants.COLUMN_HEADER).append(" @> ?::jsonb ");
            }
            Map<String, String> meta = spec.meta();
            meta.forEach((key, value) -> sb.append("AND ").append(PGConstants.COLUMN_HEADER).append(
                    " @> ?::jsonb "));
            sb.append(") ");
            predicates.add(sb.toString());
        });
        String predicatesAsString = String.join(" OR ", predicates);
        return "( " + predicatesAsString + " ) AND " + PGConstants.COLUMN_SER + ">?";
    }

    public String createSQL() {
        final String sql = "SELECT " + (selectIdOnly ? PGConstants.PROJECTION_ID
                : PGConstants.PROJECTION_FACT) + " FROM " + PGConstants.TABLE_FACT + " WHERE "
                + createWhereClause() + " ORDER BY " + PGConstants.COLUMN_SER + " ASC";
        log.trace("{} createSQL={}", req, sql);
        return sql;
    }

    public String catchupSQL(long clientId) {
        final String sql = //
                "INSERT INTO " + PGConstants.TABLE_CATCHUP + " (" + PGConstants.COLUMN_CID + ","
                        + PGConstants.COLUMN_SER + //
                        ") " + "(SELECT " + clientId + "," + //
                        PGConstants.COLUMN_SER + " FROM " + //
                        PGConstants.TABLE_FACT + " WHERE (" + createWhereClause() + //
                        ")" + " ORDER BY " + PGConstants.COLUMN_SER + " ASC)";
        log.trace("{} catchupSQL={}", req, sql);
        return sql;
    }
}
