package org.factcast.store.internal.catchup.fetching;

import java.sql.Connection;

import org.springframework.jdbc.datasource.SingleConnectionDataSource;

interface DataSourceFactory {
  SingleConnectionDataSource create(Connection target, boolean suppressClose);
}
