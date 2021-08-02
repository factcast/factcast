package org.factcast.factus.spring.tx;

import java.time.Duration;
import java.util.UUID;
import lombok.NonNull;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Named;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.projection.WriterTokenAware;
import org.springframework.transaction.PlatformTransactionManager;

abstract class AbstractSpringTxProjection
    implements SpringTxProjection, FactStreamPositionAware, WriterTokenAware, Named {
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
  public abstract UUID factStreamPosition();

  @Override
  public abstract void factStreamPosition(@NonNull UUID position);

  @Override
  public abstract WriterToken acquireWriteToken(@NonNull Duration maxWait);
}
