package org.factcast.factus.redis.tx;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Function;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.redis.AbstractRedisLens;
import org.factcast.factus.redis.RedisProjection;
import org.factcast.factus.redis.tx.RedisTransactional.Defaults;
import org.redisson.api.RTransaction;
import org.redisson.api.TransactionOptions;

@Slf4j
public class RedisTransactionalLens extends AbstractRedisLens {

  private final TransactionOptions opts;

  public RedisTransactionalLens(@NonNull RedisProjection p) {
    super(p);

    RedisTransactional transactional = p.getClass().getAnnotation(RedisTransactional.class);
    opts = Defaults.with(transactional);

    batchSize = Math.max(1, transactional.size());
    flushTimeout = calculateFlushTimeout(opts);
    log.debug(
        "Created {} instance for {} with batchsize={},timeout={}",
        getClass().getSimpleName(),
        p,
        batchSize,
        flushTimeout);
  }

  @VisibleForTesting
  long calculateFlushTimeout(TransactionOptions opts) {
    // "best" guess
    return Math.min(opts.getTimeout() / 10 * 8, Math.max(10, opts.getTimeout() - 300));
  }

  @Override
  public Function<Fact, ?> parameterTransformerFor(Class<?> type) {
    RedissonTxManager man = RedissonTxManager.get(client);
    man.options(opts);
    if (RTransaction.class.equals(type)) {
      return f -> {
        RedissonTxManager tx = man;
        tx.startOrJoin();
        return tx.getCurrentTransaction();
      };
    }
    return null;
  }

  @Override
  protected void doClear() {
    RedissonTxManager tx = RedissonTxManager.get(client);
    if (tx.inTransaction()) {
      RedissonTxManager.get(client).rollback();
    }
  }

  @Override
  protected void doFlush() {
    // otherwise we can silently commit, not to "flush" the logs
    RedissonTxManager tx = RedissonTxManager.get(client);
    if (tx.inTransaction()) {
      RedissonTxManager.get(client).commit();
    }
  }
}
