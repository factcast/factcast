/*
 * Copyright © 2017-2023 factcast.org
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.factcast.factus.redis.tx.TransactionNotificationMessages.COMMIT;
import static org.factcast.factus.redis.tx.TransactionNotificationMessages.NOTIFICATION_TOPIC_POSTFIX;
import static org.factcast.factus.redis.tx.TransactionNotificationMessages.ROLLBACK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import lombok.Getter;
import lombok.NonNull;
import org.factcast.factus.redis.tx.EnableRedisTransactionCallbacks;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

@ExtendWith(MockitoExtension.class)
class AbstractRedisSubscribedProjectionTest {

  @Mock RedissonClient client;

  @Nested
  class WithCallbacksEnabled {
    RedisSubscribedProjectionWithCallbacks underTest;
    @Mock RTopic topic;

    MessageListener<String> listener;

    @BeforeEach
    void setup() {
      when(client.getTopic(endsWith(NOTIFICATION_TOPIC_POSTFIX))).thenReturn(topic);

      when(topic.addListener(eq(String.class), any()))
          .thenAnswer(
              inv -> {
                //noinspection unchecked
                listener = inv.getArgument(1, MessageListener.class);
                return null;
              });

      // only invoke after client and topic got mocked!
      underTest = new RedisSubscribedProjectionWithCallbacks(client);

      verify(client).getTopic(any());
      verify(topic).addListener(any(), any());

      assertThat(listener).isNotNull();
    }

    @Test
    void commit() {
      // INIT
      assertThat(underTest.numberCommits()).isEqualTo(0);
      assertThat(underTest.numberRollbacks()).isEqualTo(0);

      // RUN
      // emulate we got a notification from redis
      listener.onMessage("", COMMIT.code());

      // ASSERT
      assertThat(underTest.numberCommits()).isEqualTo(1);
      assertThat(underTest.numberRollbacks()).isEqualTo(0);
    }

    @Test
    void rollBack() {
      // INIT
      assertThat(underTest.numberCommits()).isEqualTo(0);
      assertThat(underTest.numberRollbacks()).isEqualTo(0);

      // RUN
      // emulate we got a notification from redis
      listener.onMessage("", ROLLBACK.code());

      // ASSERT
      assertThat(underTest.numberCommits()).isEqualTo(0);
      assertThat(underTest.numberRollbacks()).isEqualTo(1);
    }
  }

  @Test
  void doesNotRegisterWithoutAnnotation() {
    RedisSubscribedProjectionWithoutCallbacks p =
        new RedisSubscribedProjectionWithoutCallbacks(client);

    // should not register on topic
    verify(client, never()).getTopic(any());
    verify(client, never()).getTopic(any(), any());
  }

  @Getter
  @RedisTransactional
  @ProjectionMetaData(serial = 1)
  @EnableRedisTransactionCallbacks
  class RedisSubscribedProjectionWithCallbacks extends AbstractRedisSubscribedProjection {
    int numberCommits = 0;
    int numberRollbacks = 0;

    RedisSubscribedProjectionWithCallbacks(@NonNull RedissonClient redisson) {
      super(redisson);
    }

    @Override
    public void onCommit() {
      numberCommits++;
    }

    @Override
    public void onRollback() {
      numberRollbacks++;
    }
  }

  @Getter
  @RedisTransactional
  @ProjectionMetaData(serial = 1)
  class RedisSubscribedProjectionWithoutCallbacks extends AbstractRedisSubscribedProjection {
    int numberCommits = 0;
    int numberRollbacks = 0;

    RedisSubscribedProjectionWithoutCallbacks(@NonNull RedissonClient redisson) {
      super(redisson);
    }

    @Override
    public void onCommit() {
      numberCommits++;
    }

    @Override
    public void onRollback() {
      numberRollbacks++;
    }
  }
}
