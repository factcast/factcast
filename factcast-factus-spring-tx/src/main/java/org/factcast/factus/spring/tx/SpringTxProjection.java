package org.factcast.factus.spring.tx;

import org.factcast.factus.projection.Projection;
import org.springframework.transaction.PlatformTransactionManager;

@SpringTransactional
public interface SpringTxProjection extends Projection {
  PlatformTransactionManager platformTransactionManager();
}
