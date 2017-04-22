package org.factcast.store.pgsql.internal;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Predicate;

import com.impossibl.postgres.api.jdbc.PGConnection;

import lombok.extern.slf4j.Slf4j;

/**
 * Used to test if a connection is still alive.
 * 
 * Even though CPools provide this already, this one is intended to used with
 * the cone {@link PGConnection}, that listens to changes on the fact table and
 * thus should not be reused in a CPool.
 * 
 * @author usr
 *
 */

@Slf4j
class ConnectionTester implements Predicate<java.sql.Connection> {

	@Override
	public boolean test(java.sql.Connection connection) {

		if (connection != null) {
			try (PreparedStatement s = connection.prepareStatement("SELECT 42"); ResultSet rs = s.executeQuery();) {
				rs.next();
				return rs.getInt(1) == 42;
			} catch (SQLException e) {
				log.warn("Connection test failed:", e);
			}
		}
		return false;

	}

}
