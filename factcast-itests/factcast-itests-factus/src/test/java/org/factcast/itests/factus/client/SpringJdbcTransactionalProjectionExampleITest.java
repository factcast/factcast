/*
 * Copyright © 2017-2022 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.itests.factus.client;

import static java.util.UUID.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.itests.factus.proj.SpringJdbcTransactionalProjectionExample;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@Slf4j
@EnableAutoConfiguration
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

  protected void createTables() {
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
