package org.factcast.store.pgsql.internal;

import javax.annotation.Nonnull;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

import lombok.RequiredArgsConstructor;

/**
 * executes a query in a synchronized fashion, to make sure, results are
 * processed in order as well as sequentially.
 * 
 * DO NOT use an instance as a singleton/Spring bean. This class is meant be
 * instantiated by each subscription.
 * 
 * @author usr
 *
 */
@RequiredArgsConstructor
@Nonnull
class PGSynchronizedQuery implements Runnable {

	final JdbcTemplate jdbcTemplate;
	final String sql;
	final PreparedStatementSetter setter;
	final RowCallbackHandler rowHandler;

	@Override
	// the synchronized here is crucial!
	public synchronized void run() {
		jdbcTemplate.query(sql, setter, rowHandler);
	}

}