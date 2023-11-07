/*
 * Copyright © 2017-2022 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.factus.redis;

import lombok.NonNull;
import org.factcast.factus.projection.SubscribedProjection;
import org.factcast.factus.redis.tx.EnableRedisTransactionCallbacks;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.redis.tx.TransactionNotificationMessages;
import org.redisson.api.RedissonClient;

public abstract class AbstractRedisSubscribedProjection extends AbstractRedisProjection
    implements SubscribedProjection {

  public AbstractRedisSubscribedProjection(@NonNull RedissonClient redisson) {
    super(redisson);

    subscribeToTransactionNotificationIfEnabled();
  }

  private void subscribeToTransactionNotificationIfEnabled() {

    if (getClass().getAnnotation(EnableRedisTransactionCallbacks.class) == null
        || getClass().getAnnotation(RedisTransactional.class) == null) {
      return;
    }

    getLogger().debug("Registering to receive commit and rollback notifications.");

    redisson()
        .getTopic(TransactionNotificationMessages.getTopicName(this))
        .addListener(
            String.class,
            (channel, code) -> {
              switch (TransactionNotificationMessages.fromCode(code)) {
                case COMMIT:
                  onCommit();
                  break;

                case ROLLBACK:
                  onRollback();
                  break;

                default:
                  getLogger()
                      .warn(
                          "Received unknown code {} from channel {} in transaction notification subscription.",
                          code,
                          channel);
              }
            });
  }

  /**
   * <b>Will only be called when the projection is annotated with <code>
   * @EnableRedisTransactionCallbacks</code>!</b>
   *
   * <p>Invoked when a transaction was successfully committed, no matter if this instance is
   * processing events, or another one. Can be used to update internal caches from redis on all
   * instances, after the data in redis got updated by new events.
   */
  public void onCommit() {
    // default: do nothing
  }

  /**
   * <b>Will only be called when the projection is annotated with <code>
   * @EnableRedisTransactionCallbacks</code>!</b>
   *
   * <p>Invoked when a transaction was rolled back. Unfortunately we do not have more context.
   */
  public void onRollback() {
    // default: do nothing
  }
}
