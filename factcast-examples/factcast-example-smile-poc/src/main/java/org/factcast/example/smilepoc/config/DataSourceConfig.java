package org.factcast.example.smilepoc.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.factcast.example.smilepoc.PocProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DataSourceConfig {

  @Bean
  DataSource dataSource(PocProperties props) {
    String url = props.postgres().url();
    String separator = url.contains("?") ? "&" : "?";
    String tunedUrl =
        url + separator + "binaryTransfer=true&prepareThreshold=1&reWriteBatchedInserts=true";

    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(tunedUrl);
    ds.setUsername(props.postgres().user());
    ds.setPassword(props.postgres().password());
    ds.setMaximumPoolSize(4);
    ds.setAutoCommit(true);
    return ds;
  }

  @Bean
  JdbcTemplate jdbcTemplate(DataSource ds) {
    return new JdbcTemplate(ds);
  }
}
