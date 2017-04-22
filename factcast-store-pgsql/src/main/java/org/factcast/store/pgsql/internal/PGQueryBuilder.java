package org.factcast.store.pgsql.internal;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.subscription.FactSpec;
import org.springframework.jdbc.core.PreparedStatementSetter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Provides {@link PreparedStatementSetter} and the corresponding SQL from a
 * list of {@link FactSpec}s.
 * 
 * @author usr
 *
 */
@RequiredArgsConstructor
class PGQueryBuilder {

	// TODO is that possibly interesting to configure?
	private static final int FETCH_SIZE = 50;
	@NonNull
	private final List<FactSpec> specs;

	PreparedStatementSetter createStatementSetter(AtomicLong ser) {

		return p -> {
			// be conservative, less ram and fetching from db is less of a
			// problem than serializing to the client
			//
			// Note, that by sync. calling the Observer, backpressure is kind of
			// built-in.
			p.setFetchSize(FETCH_SIZE);

			// TODO vulnerable of json injection attack
			int count = 0;
			for (FactSpec spec : specs) {

				p.setString(++count, "{\"ns\": \"" + spec.ns() + "\" }");

				String type = spec.type();
				if (type != null) {
					p.setString(++count, "{\"type\": \"" + type + "\" }");
				}

				UUID agg = spec.aggId();
				if (agg != null) {
					p.setString(++count, "{\"aggId\": \"" + agg.toString() + "\" }");
				}

				Map<String, String> meta = spec.meta();
				for (Entry<String, String> e : meta.entrySet()) {
					p.setString(++count, "{\"meta\":{\"" + e.getKey() + "\":\"" + e.getValue() + "\" }}");
				}
			}

			p.setLong(++count, ser.get());
		};
	}

	private String createWhereClause() {

		StringBuilder sb = new StringBuilder();
		sb.append("( (1=0) ");
		specs.forEach(spec -> {
			sb.append("OR ( ");

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
		});
		sb.append(") AND " + PGConstants.COLUMN_SER + ">? ");

		return sb.toString();
	}

	String createSQL() {
		return "SELECT " + PGConstants.PROJECTION_FACT + " FROM " + PGConstants.TABLE_FACT + " WHERE "
				+ createWhereClause() + " ORDER BY " + PGConstants.COLUMN_SER + " ASC";
	}
}
