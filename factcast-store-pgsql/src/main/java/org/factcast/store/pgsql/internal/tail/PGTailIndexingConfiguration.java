package org.factcast.store.pgsql.internal.tail;

import org.factcast.store.pgsql.PgConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PGTailIndexingConfiguration {

  @Bean
  public PGTailIndexManagerImpl pgTailIndexManager(
      JdbcTemplate jdbc, PgConfigurationProperties props) {
    return new PGTailIndexManagerImpl(jdbc, props);
  }
}
