package org.factcast.factus.spring.tx;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Function;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.projector.AbstractTransactionalLens;
import org.factcast.factus.projector.ProjectorLens;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class SpringTransactionalLens extends AbstractTransactionalLens implements ProjectorLens {
  private final PlatformTransactionManager transactionManager;
  private final SpringTxManager txManager;

  @VisibleForTesting
  SpringTransactionalLens(
      @NonNull SpringTxProjection springTxProjection,
      @NonNull SpringTxManager txManager,
      @NonNull TransactionDefinition definition) {
    super(springTxProjection);

    this.txManager = txManager;
    this.transactionManager = springTxProjection.platformTransactionManager();

    flushTimeout = calculateFlushTimeout(definition);
    bulkSize = Math.max(1, getSize(springTxProjection));
  }

  public SpringTransactionalLens(@NonNull SpringTxProjection springTxProjection) {
    this(
        springTxProjection,
        new SpringTxManager(
            springTxProjection.platformTransactionManager(), creatDefinition(springTxProjection)),
        creatDefinition(springTxProjection));
  }

  @Override
  public Function<Fact, ?> parameterTransformerFor(Class<?> type) {
    if (TransactionTemplate.class.equals(type)) {
      return f -> new TransactionTemplate(transactionManager);
    }
    return null;
  }

  @Override
  public void beforeFactProcessing(Fact f) {
    super.beforeFactProcessing(f);

    // starting a new tx or join a current one
    txManager.startOrJoin();
  }

  @Override
  protected void doClear() {
    txManager.rollback();
  }

  @Override
  protected void doFlush() {
    txManager.commit();
  }

  @VisibleForTesting
  static int getSize(SpringTxProjection p) {
    SpringTransactional transactional = p.getClass().getAnnotation(SpringTransactional.class);
    if (transactional == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + SpringTransactional.class.getSimpleName());
    }
    return transactional.size();
  }

  @VisibleForTesting
  static TransactionDefinition creatDefinition(@NonNull SpringTxProjection p) {
    SpringTransactional transactional = p.getClass().getAnnotation(SpringTransactional.class);
    if (transactional == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + SpringTransactional.class.getSimpleName());
    }
    return SpringTransactional.Defaults.with(transactional);
  }

  @VisibleForTesting
  static long calculateFlushTimeout(@NonNull TransactionDefinition opts) {
    // "best" guess
    long flush = opts.getTimeout() / 10 * 8;
    if (flush < 80) {
      // disable batching altogether as it is too risky
      flush = 0;
    }
    return flush;
  }
}
