package org.factcast.factus.redis;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.factus.projection.ManagedProjection;
import org.factcast.factus.projection.WriterToken;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;

// TODO extract IF

public class AbstractRedisProjection extends ManagedProjection {

  protected final RedissonClient redisson;

  @Getter(AccessLevel.PACKAGE)
  protected final RedissonTXManager redissonTXManager;

  private final RLock lock;
  private final String stateBucketName;

  public AbstractRedisProjection(@NonNull RedissonClient redisson) {
    // needs to be free from transactions, obviously
    lock = redisson.getLock("lock_" + getClass().getName());
    redissonTXManager = new RedissonTXManager(redisson);
    this.redisson = redisson;
    String bucket_suffix = createRedisKey();
    stateBucketName = bucket_suffix + "_state_tracking";
  }

  protected String createRedisKey() {
    Class<?> c = getClass();
    while (c.getName().contains("$$EnhancerBySpring") || c.getName().contains("CGLIB")) {
      c = c.getSuperclass();
    }
    // TODO add serial from ProjectionMetaData
    return c.getSimpleName();
  }

  private RBucket<UUID> stateBucket(RTransaction redisson) {
    return redisson.getBucket(stateBucketName, UUIDCodec.INSTANCE);
  }

  @Override
  public UUID state() {
    return redissonTXManager.joinOrAutoCommit(
        (Function<RTransaction, UUID>) tx -> stateBucket(tx).get());
  }

  @Override
  public void state(@NonNull UUID state) {
    redissonTXManager.joinOrAutoCommit(
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
