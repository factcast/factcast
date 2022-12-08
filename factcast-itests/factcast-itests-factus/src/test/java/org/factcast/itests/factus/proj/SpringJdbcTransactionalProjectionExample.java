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
package org.factcast.itests.factus.proj;

import java.time.Duration;
import java.util.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.spring.tx.AbstractSpringTxManagedProjection;
import org.factcast.factus.spring.tx.SpringTransactional;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

public class SpringJdbcTransactionalProjectionExample {
  private SpringJdbcTransactionalProjectionExample() {}

  @Slf4j
  @ProjectionMetaData(serial = 1)
  @SpringTransactional
  public static class UserNames extends AbstractSpringTxManagedProjection {

    private final JdbcTemplate jdbcTemplate;

    public UserNames(
        @NonNull PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
      super(platformTransactionManager);
      this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> getUserNames() {
      return jdbcTemplate.query("SELECT name FROM users", (rs, rowNum) -> rs.getString(1));
    }

    @Handler
    void apply(UserCreated e) {
      log.info("received event: " + e);
      jdbcTemplate.update(
          "INSERT INTO users (name, id) VALUES (?,?);", e.userName(), e.aggregateId());
    }

    @Handler
    void apply(UserDeleted e) {
      log.info("received event: " + e);
      jdbcTemplate.update("DELETE FROM users where id = ?", e.aggregateId());
    }

    @Override
    public UUID factStreamPosition() {
      try {
        return jdbcTemplate.queryForObject(
            "SELECT fact_stream_position FROM fact_stream_positions WHERE projection_name = ?",
            UUID.class,
            getScopedName().asString());
      } catch (IncorrectResultSizeDataAccessException e) {
        // no state yet, just return null
        return null;
      }
    }

    @Override
    public void factStreamPosition(@NonNull UUID factStreamPosition) {
      jdbcTemplate.update(
          "INSERT INTO fact_stream_positions (projection_name, fact_stream_position) "
              + "VALUES (?, ?) "
              + "ON CONFLICT (projection_name) DO UPDATE SET fact_stream_position = ?",
          getScopedName().asString(),
          factStreamPosition,
          factStreamPosition);
    }

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      return () -> {};
    }
  }
}
