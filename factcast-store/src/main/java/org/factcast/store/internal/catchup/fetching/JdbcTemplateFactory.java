package org.factcast.store.internal.catchup.fetching;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

interface JdbcTemplateFactory {
  JdbcTemplate create(DataSource dataSource);
}
