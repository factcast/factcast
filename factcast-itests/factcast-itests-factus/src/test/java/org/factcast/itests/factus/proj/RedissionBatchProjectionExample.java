package org.factcast.itests.factus.proj;

import org.factcast.factus.Handler;
import org.factcast.factus.redis.AbstractRedisManagedProjection;
import org.factcast.factus.redis.batch.RedisBatched;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.redisson.api.RBatch;
import org.redisson.api.RMap;
import org.redisson.api.RMapAsync;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RedissionBatchProjectionExample {

  @ProjectionMetaData(serial = 1)
  @RedisBatched
  public static class UserNames extends AbstractRedisManagedProjection {

    public UserNames(RedissonClient redisson) {
      super(redisson);
    }

    public List<String> getUserNames() {
      RMap<UUID, String> userNames = redisson.getMap(redisKey());
      return new ArrayList<>(userNames.values());
    }

    @Handler
    void apply(UserCreated created, RBatch batch) {
      RMapAsync<UUID, String> userNames = batch.getMap(redisKey());
      userNames.putAsync(created.aggregateId(), created.userName());
    }

    @Handler
    void apply(UserDeleted deleted, RBatch batch) {
      batch.getMap(redisKey()).removeAsync(deleted.aggregateId());
    }
  }
}
