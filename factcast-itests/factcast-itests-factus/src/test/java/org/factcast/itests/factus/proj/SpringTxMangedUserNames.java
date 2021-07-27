package org.factcast.itests.factus.proj;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
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

@Slf4j
@ProjectionMetaData(serial = 1)
@SpringTransactional
public class SpringTxMangedUserNames extends AbstractSpringTxManagedProjection {

  private final JdbcTemplate jdbcTemplate;

  public SpringTxMangedUserNames(
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
  public UUID state() {
    try {
      return jdbcTemplate.queryForObject(
          "SELECT state FROM managed_projection WHERE name = ?",
          UUID.class,
          getScopedName().asString());
    } catch (IncorrectResultSizeDataAccessException e) {
      // no state yet, just return null
      return null;
    }
  }

  @Override
  public void state(@NonNull UUID state) {
    jdbcTemplate.update(
        "INSERT INTO managed_projection (name, state) VALUES (?, ?) ON CONFLICT (name) DO UPDATE SET state = ?",
        getScopedName().asString(),
        state,
        state);
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return () -> {};
  }
}
