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

  protected final RedissonClient redisson;

  private final RLock lock;
  private final String stateBucketName;

  @Getter private final String redisKey;

  @Getter private final RedissonTxManager redissonTxManager;

  public AbstractRedisProjection(@NonNull RedissonClient redisson) {
    // needs to be free from transactions, obviously
    this.redisson = redisson;
    redissonTxManager = new RedissonTxManager(redisson);

    redisKey = createRedisKey();
    lock = redisson.getLock(redisKey + "_lock");
    stateBucketName = redisKey + "_state_tracking";
  }

  private RBucket<UUID> stateBucket(RTransaction redisson) {
    return redisson.getBucket(stateBucketName, UUIDCodec.INSTANCE);
  }

  @Override
  public UUID state() {
    return redissonTxManager.joinOrAutoCommit(
        (Function<RTransaction, UUID>) tx -> stateBucket(tx).get());
  }

  @Override
  public void state(@NonNull UUID state) {

    redissonTxManager.joinOrAutoCommit(
        tx -> {
          stateBucket(tx).set(state);
        });
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    lock.lock();
    return new RedisWriterToken(redisson, lock);
  }
}
