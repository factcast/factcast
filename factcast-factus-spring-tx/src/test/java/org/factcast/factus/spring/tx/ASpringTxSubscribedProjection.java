package org.factcast.factus.spring.tx;

import java.time.Duration;
import java.util.UUID;
import lombok.NonNull;
import org.factcast.factus.projection.WriterToken;
import org.springframework.transaction.PlatformTransactionManager;

@SpringTransactional
public class ASpringTxSubscribedProjection extends AbstractSpringTxSubscribedProjection {
  public ASpringTxSubscribedProjection(
      @NonNull PlatformTransactionManager platformTransactionManager) {
    super(platformTransactionManager);
  }

  @Override
  public UUID state() {
    return null;
  }

  @Override
  public void state(@NonNull UUID state) {}

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return null;
  }
}
