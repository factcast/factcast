package org.factcast.test;

import java.sql.DriverManager;
import java.util.Properties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public class PostgresEraser implements BetweenTestsEraser {
  @SneakyThrows
  @Override
  public void wipe(Object testInstance) {

    if (testInstance instanceof PostgresContainerTest) {

      val pg = ((PostgresContainerTest) testInstance).getPostgresContainer();
      val url = pg.getJdbcUrl();
      Properties p = new Properties();
      p.put("user", pg.getUsername());
      p.put("password", pg.getPassword());

      log.trace("erasing postgres state in between tests for {}", url);

      try (val con = DriverManager.getConnection(url, p);
          val st = con.createStatement()) {
        st.execute("TRUNCATE fact");
        st.execute("TRUNCATE tokenstore");
        st.execute("TRUNCATE transformationcache");
        st.execute("TRUNCATE snapshot_cache");
      }
    } else {
      log.trace("Test does not implement PostgresContainerTest - skipping");
    }
  }
}
