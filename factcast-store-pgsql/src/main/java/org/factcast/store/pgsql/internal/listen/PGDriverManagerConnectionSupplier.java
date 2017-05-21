package org.factcast.store.pgsql.internal.listen;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.postgresql.jdbc.PgConnection;
import org.springframework.stereotype.Component;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PGDriverManagerConnectionSupplier {
    @NonNull
    final org.apache.tomcat.jdbc.pool.DataSource ds;

    public PgConnection get() {
        try {
            return (PgConnection) DriverManager.getDriver(ds.getUrl()).connect(ds.getUrl(), ds
                    .getDbProperties());
        } catch (SQLException e) {
            final String msg = "Cannot acquire Connection from DriverManager: " + ds.getUrl();
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

}
