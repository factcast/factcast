package org.factcast.store.internal.tail;

import org.factcast.store.StoreConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PGTailIndexingConfiguration {

  @Bean
  public PGTailIndexManager pgTailIndexManager(
      JdbcTemplate jdbc, StoreConfigurationProperties props) {
    return new PGTailIndexManagerImpl(jdbc, props);
  }
}
