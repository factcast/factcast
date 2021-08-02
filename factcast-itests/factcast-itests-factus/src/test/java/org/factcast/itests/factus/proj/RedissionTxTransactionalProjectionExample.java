package org.factcast.itests.factus.proj;

import org.factcast.factus.Handler;
import org.factcast.factus.redis.AbstractRedisManagedProjection;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.redisson.api.RMap;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;

import java.util.Collection;
import java.util.UUID;

public class RedissionTxTransactionalProjectionExample {

  @ProjectionMetaData(serial = 1)
  @RedisTransactional
  public static class UserNames extends AbstractRedisManagedProjection {

    public UserNames(RedissonClient redisson) {
      super(redisson);
    }

    public Collection<String> getUserNames() {
      RMap<UUID, String> userNames = redisson.getMap(redisKey());
      return userNames.values();
    }

    @Handler
    protected void apply(UserCreated created, RTransaction tx) {
      RMap<UUID, String> userNames = tx.getMap(redisKey());
      userNames.put(created.aggregateId(), created.userName());
    }

    @Handler
    protected void apply(UserDeleted deleted, RTransaction tx) {
      tx.getMap(redisKey()).remove(deleted.aggregateId());
    }
  }
}
