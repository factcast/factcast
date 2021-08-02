package org.factcast.factus.spring.tx;

import java.time.Duration;
import java.util.UUID;
import lombok.NonNull;
import org.factcast.factus.projection.WriterToken;
import org.springframework.transaction.PlatformTransactionManager;

@SpringTransactional
public class ASpringTxManagedProjection extends AbstractSpringTxManagedProjection {
  public ASpringTxManagedProjection(
      @NonNull PlatformTransactionManager platformTransactionManager) {
    super(platformTransactionManager);
  }

  @Override
  public UUID factStreamPosition() {
    return null;
  }

  @Override
  public void factStreamPosition(@NonNull UUID position) {}

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return null;
  }
}
