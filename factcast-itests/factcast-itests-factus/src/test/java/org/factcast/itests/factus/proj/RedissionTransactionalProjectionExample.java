package org.factcast.itests.factus.proj;

import org.factcast.factus.Handler;
import org.factcast.factus.redis.AbstractRedisManagedProjection;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RedissionTransactionalProjectionExample {

  @ProjectionMetaData(serial = 1)
  @RedisTransactional
  public static class UserNames extends AbstractRedisManagedProjection {

    public UserNames(RedissonClient redisson) {
      super(redisson);
    }

    public List<String> getUserNames() {
      Map<UUID, String> userNames = redisson.getMap(redisKey());
      return new ArrayList<>(userNames.values());
    }

    @Handler
    void apply(UserCreated e, RTransaction tx) {
      Map<UUID, String> userNames = tx.getMap(redisKey());
      userNames.put(e.aggregateId(), e.userName());
    }

    @Handler
    void apply(UserDeleted e, RTransaction tx) {
      tx.getMap(redisKey()).remove(e.aggregateId());
    }
  }
}
