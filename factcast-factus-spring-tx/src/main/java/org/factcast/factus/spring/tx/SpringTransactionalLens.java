package org.factcast.factus.spring.tx;

import com.google.common.annotations.VisibleForTesting;

import java.util.function.Function;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.factus.projector.AbstractTransactionalLens;
import org.factcast.factus.projector.ProjectorLens;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class SpringTransactionalLens extends AbstractTransactionalLens implements ProjectorLens {

  private final PlatformTransactionManager transactionManager;

  private final TransactionDefinition definition;

  private TransactionStatus currentTx;

  public SpringTransactionalLens(@NonNull SpringTxProjection jdbcProjection) {
    super(jdbcProjection);

    this.transactionManager = jdbcProjection.platformTransactionManager();
    log.debug("Init");
    bulkSize = Math.max(1, getSize(jdbcProjection));
    definition = createOpts(jdbcProjection);
    flushTimeout = calculateFlushTimeout(definition);
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

    // starting a new tx
    startOrJoin();
  }

  @Override
  protected void doClear() {
    transactionManager.rollback(startOrJoin());

    currentTx = null;
  }

  @Override
  protected void doFlush() {
    val transaction = startOrJoin();
    transactionManager.commit(transaction);

    currentTx = null;
  }

  private TransactionStatus startOrJoin() {
    if (currentTx == null) {
      currentTx = transactionManager.getTransaction(definition);
    }

    return currentTx;
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
  static TransactionDefinition createOpts(@NonNull SpringTxProjection p) {
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
