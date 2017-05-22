package org.factcast.store.pgsql.internal.listen;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.sql.DataSource;

import org.postgresql.jdbc.PgConnection;
import org.springframework.stereotype.Component;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PGDriverManagerConnectionSupplier implements Supplier<PgConnection> {
    @NonNull
    final org.apache.tomcat.jdbc.pool.DataSource ds;

    @Inject
    PGDriverManagerConnectionSupplier(@NonNull DataSource dataSource) {

        if (dataSource instanceof org.apache.tomcat.jdbc.pool.DataSource) {
            this.ds = (org.apache.tomcat.jdbc.pool.DataSource) dataSource;
        } else {
            throw new IllegalStateException("expected "
                    + org.apache.tomcat.jdbc.pool.DataSource.class.getName() + " , but got "
                    + dataSource.getClass().getName());
        }
    }

    @Override
    public PgConnection get() {
        try {

            return (PgConnection) DriverManager.getDriver(ds.getUrl()).connect(ds.getUrl(),
                    buildCredentialProperties(ds));
        } catch (SQLException e) {
            final String msg = "Cannot acquire Connection from DriverManager: " + ds.getUrl();
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    private Properties buildCredentialProperties(org.apache.tomcat.jdbc.pool.DataSource ds) {

        Properties dbp = new Properties();
        dbp.setProperty("user", ds.getPoolProperties().getUsername());
        dbp.setProperty("password", ds.getPoolProperties().getPassword());

        return dbp;
    }

}
