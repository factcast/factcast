package org.factcast.factus.spring.tx;

import java.time.Duration;
import lombok.NonNull;
import org.factcast.factus.projection.SubscribedProjection;
import org.factcast.factus.projection.WriterToken;
import org.springframework.transaction.PlatformTransactionManager;

public abstract class AbstractSpringTxSubscribedProjection extends AbstractSpringTxProjection
    implements SubscribedProjection {
  public AbstractSpringTxSubscribedProjection(
      @NonNull PlatformTransactionManager platformTransactionManager) {
    super(platformTransactionManager);
  }

  @Override
  public abstract WriterToken acquireWriteToken(@NonNull Duration maxWait);
}
