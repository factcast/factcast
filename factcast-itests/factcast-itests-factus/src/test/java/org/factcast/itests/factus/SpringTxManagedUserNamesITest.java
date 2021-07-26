package org.factcast.itests.factus;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.factus.Factus;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.itests.factus.proj.SpringTxMangedUserNames;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Arrays;

import static java.util.UUID.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Slf4j
public class SpringTxManagedUserNamesITest extends AbstractFactCastIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager platformTransactionManager;

    @Autowired
    Factus factus;

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
        val event1 = new UserCreated(randomUUID(), "Peter");
        val event2 = new UserCreated(randomUUID(), "Paul");
        val event3 = new UserCreated(randomUUID(), "Klaus");
        val event4 = new UserDeleted(event3.aggregateId());

        log.info("Publishing test events");
        factus.publish(Arrays.asList(event1, event2, event3, event4));

        val uut = new SpringTxMangedUserNames(platformTransactionManager, jdbcTemplate);
        factus.update(uut);
        val userNames = uut.getUserNames();

        assertThat(userNames).containsExactlyInAnyOrder("Peter", "Paul");
    }

    private void createTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS managed_projection;");
        jdbcTemplate.execute(
                "CREATE TABLE managed_projection (\n"
                        + "\n"
                        + "    name  varchar(255),\n"
                        + "    state UUID,\n"
                        + "\n"
                        + "    PRIMARY KEY (name)\n"
                        + ");");

        jdbcTemplate.execute("DROP TABLE IF EXISTS users;");
        jdbcTemplate.execute(
                "CREATE TABLE users (\n"
                        + "\n"
                        + "    name  varchar(255),\n"
                        + "    id UUID,\n"
                        + "\n"
                        + "    PRIMARY KEY (id)\n"
                        + ");");
    }
}
