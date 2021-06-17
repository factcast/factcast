package org.factcast.factus.spring.tx;

import lombok.NonNull;
import lombok.val;
import org.factcast.factus.projection.Projection;
import org.springframework.transaction.PlatformTransactionManager;

public interface SpringTxProjection extends Projection {

  PlatformTransactionManager platformTransactionManager();
}
