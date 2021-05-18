package org.factcast.test;

import org.testcontainers.containers.PostgreSQLContainer;

@SuppressWarnings("rawtypes")
public interface PostgresContainerTest {
  PostgreSQLContainer getPostgresContainer();
}
