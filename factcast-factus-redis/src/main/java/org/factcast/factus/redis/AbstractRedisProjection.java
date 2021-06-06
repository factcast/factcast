package org.factcast.factus.redis;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.factus.projection.WriterToken;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;

public class AbstractRedisProjection implements RedisProjection {
  @Getter protected final RedissonClient redisson;

  private final RLock lock;
  private final String stateBucketName;

  @Getter private final String redisKey;

  public AbstractRedisProjection(@NonNull RedissonClient redisson) {
    this.redisson = redisson;

    redisKey = createRedisKey();
    stateBucketName = redisKey + "_state_tracking";

    // needs to be free from transactions, obviously
    lock = redisson.getLock(redisKey + "_lock");
  }

  private RBucket<UUID> stateBucket(RTransaction tx) {
    return tx.getBucket(stateBucketName, UUIDCodec.INSTANCE);
  }

  private RBucket<UUID> stateBucket() {
    return redisson.getBucket(stateBucketName, UUIDCodec.INSTANCE);
  }

  @Override
  public UUID state() {
    RedissonTxManager man = RedissonTxManager.get(redisson);
    if (man.inTransaction()) {
      return man.join((Function<RTransaction, UUID>) tx -> stateBucket(tx).get());
    } else {
      return stateBucket().get();
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void state(@NonNull UUID state) {
    RedissonTxManager man = RedissonTxManager.get(redisson);
    if (man.inTransaction()) {
      man.join(
          tx -> {
            stateBucket(man.getCurrentTransaction()).set(state);
          });
    } else {
      stateBucket().set(state);
    }
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    lock.lock();
    return new RedisWriterToken(redisson, lock);
  }
}
