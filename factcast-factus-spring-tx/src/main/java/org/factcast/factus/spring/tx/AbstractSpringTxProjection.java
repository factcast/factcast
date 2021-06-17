package org.factcast.factus.spring.tx;

import lombok.NonNull;
import org.factcast.factus.projection.StateAware;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.projection.WriterTokenAware;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.UUID;

abstract class AbstractSpringTxProjection
    implements SpringTxProjection, StateAware, WriterTokenAware {
  private final PlatformTransactionManager platformTransactionManager;

  public AbstractSpringTxProjection(
      @NonNull PlatformTransactionManager platformTransactionManager) {
    this.platformTransactionManager = platformTransactionManager;
  }

  @Override
  public PlatformTransactionManager platformTransactionManager() {
    return platformTransactionManager;
  }

  @Override
  public abstract UUID state();

  @Override
  public abstract void state(@NonNull UUID state);

  @Override
  public abstract WriterToken acquireWriteToken(@NonNull Duration maxWait);
}
