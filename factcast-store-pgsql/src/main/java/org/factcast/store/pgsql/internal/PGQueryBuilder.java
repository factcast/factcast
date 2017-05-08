package org.factcast.store.pgsql.internal;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.springframework.jdbc.core.PreparedStatementSetter;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides {@link PreparedStatementSetter} and the corresponding SQL from a
 * list of {@link FactSpec}s.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@Slf4j
class PGQueryBuilder {

    // be conservative, less ram and fetching from db often is less of a
    // problem than serializing to the client.
    //
    // Note, that by sync. calling the Observer, (depending on the
    // transport) backpressure is kind of built-in.
    static final int FETCH_SIZE = 100;

    final boolean selectIdOnly;

    @NonNull
    final SubscriptionRequestTO req;

    PGQueryBuilder(@NonNull SubscriptionRequestTO request) {
        this.req = request;
        selectIdOnly = request.idOnly() && !request.hasAnyScriptFilters();
    }

    PreparedStatementSetter createStatementSetter(AtomicLong serial) {

        return p -> {
            p.setFetchSize(FETCH_SIZE);

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

            sb.append(PGConstants.COLUMN_HEADER + " @> ? ");

            String type = spec.type();
            if (type != null) {
                sb.append("AND " + PGConstants.COLUMN_HEADER + " @> ? ");
            }

            UUID agg = spec.aggId();
            if (agg != null) {
                sb.append("AND " + PGConstants.COLUMN_HEADER + " @> ? ");
            }

            Map<String, String> meta = spec.meta();
            meta.entrySet().forEach(e -> {
                sb.append("AND " + PGConstants.COLUMN_HEADER + " @> ? ");
            });

            sb.append(") ");

            predicates.add(sb.toString());
        });

        String predicatesAsString = String.join(" OR ", predicates);
        return "( " + predicatesAsString + " ) AND " + PGConstants.COLUMN_SER + ">?";
    }

    String createSQL() {
        final String sql = "SELECT " + (selectIdOnly ? PGConstants.PROJECTION_ID
                : PGConstants.PROJECTION_FACT) + " FROM " + PGConstants.TABLE_FACT + " WHERE "
                + createWhereClause() + " ORDER BY " + PGConstants.COLUMN_SER + " ASC";
        log.info("{} SQL={}", req, sql);
        return sql;
    }
}
