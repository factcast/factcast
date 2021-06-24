package org.factcast.factus.spring.tx;

import lombok.NonNull;
import org.springframework.transaction.PlatformTransactionManager;

public abstract class AbstractSpringTxManagedProjection extends AbstractSpringTxProjection
    implements SpringTxManagedProjection {
  public AbstractSpringTxManagedProjection(
      @NonNull PlatformTransactionManager platformTransactionManager) {
    super(platformTransactionManager);
  }
}
