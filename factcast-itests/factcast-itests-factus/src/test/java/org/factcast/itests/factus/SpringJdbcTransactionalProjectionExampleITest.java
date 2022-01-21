package org.factcast.itests.factus;

import static java.util.UUID.*;
import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.itests.factus.proj.SpringJdbcTransactionalProjectionExample;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@Slf4j
@DirtiesContext
public class SpringJdbcTransactionalProjectionExampleITest extends AbstractFactCastIntegrationTest {

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired PlatformTransactionManager platformTransactionManager;

  @Autowired Factus factus;

  public static PostgreSQLContainer postgreSQLContainer =
      new PostgreSQLContainer("postgres:11.5").withPassword("sa").withUsername("sa");

  static {
    postgreSQLContainer.start();

    System.setProperty("spring.datasource.url", postgreSQLContainer.getJdbcUrl());
    System.setProperty("spring.datasource.username", postgreSQLContainer.getUsername());
    System.setProperty("spring.datasource.password", postgreSQLContainer.getPassword());
  }

  @BeforeEach
  void setUp() {
    createTables();
  }

  @Test
  void readingNamesFromProjection() {
    var event1 = new UserCreated(randomUUID(), "Peter");
    var event2 = new UserCreated(randomUUID(), "Paul");
    var event3 = new UserCreated(randomUUID(), "Klaus");
    var event4 = new UserDeleted(event3.aggregateId());

    log.info("Publishing test events");
    factus.publish(Arrays.asList(event1, event2, event3, event4));

    var uut =
        new SpringJdbcTransactionalProjectionExample.UserNames(
            platformTransactionManager, jdbcTemplate);
    factus.update(uut);
    var userNames = uut.getUserNames();

    assertThat(userNames).containsExactlyInAnyOrder("Peter", "Paul");
  }

  private void createTables() {
    jdbcTemplate.execute("DROP TABLE IF EXISTS fact_stream_positions;");
    jdbcTemplate.execute(
        "CREATE TABLE fact_stream_positions (\n"
            + "\n"
            + "    projection_name TEXT,\n"
            + "    fact_stream_position UUID,\n"
            + "\n"
            + "    PRIMARY KEY (projection_name)\n"
            + ");");

    jdbcTemplate.execute("DROP TABLE IF EXISTS users;");
    jdbcTemplate.execute(
        "CREATE TABLE users (\n"
            + "\n"
            + "    name  TEXT,\n"
            + "    id UUID,\n"
            + "\n"
            + "    PRIMARY KEY (id)\n"
            + ");");
  }
}
