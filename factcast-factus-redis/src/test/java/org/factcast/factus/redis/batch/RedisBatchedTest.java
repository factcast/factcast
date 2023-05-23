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
package org.factcast.factus.redis.batch;

import static org.assertj.core.api.Assertions.*;

import org.factcast.factus.redis.batch.RedisBatched.Defaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.BatchOptions;

@ExtendWith(MockitoExtension.class)
public class RedisBatchedTest {

  @Test
  void defaults() {
    RedisBatched rb1 = RB1.class.getAnnotation(RedisBatched.class);
    RedisBatched rb2 = RB2.class.getAnnotation(RedisBatched.class);

    BatchOptions defaults = Defaults.create();
    BatchOptions withRb1 = Defaults.with(rb1);
    BatchOptions withRb2 = Defaults.with(rb1);

    assertThat(withRb1).isEqualToComparingFieldByField(defaults);
    assertThat(withRb2).isEqualToComparingFieldByField(defaults);
  }

  @Test
  void responseTimeout() {
    BatchOptions t = Defaults.with(RB3.class.getAnnotation(RedisBatched.class));
    BatchOptions defaults = Defaults.create();
    assertThat(t.getResponseTimeout()).isEqualTo(123);
    assertThat(t).extracting(BatchOptions::getRetryAttempts).isEqualTo(defaults.getRetryAttempts());
    assertThat(t).extracting(BatchOptions::getRetryInterval).isEqualTo(defaults.getRetryInterval());
  }

  @Test
  void attempts() {
    BatchOptions t = Defaults.with(RB4.class.getAnnotation(RedisBatched.class));
    BatchOptions defaults = Defaults.create();
    assertThat(t)
        .extracting(BatchOptions::getResponseTimeout)
        .isEqualTo(defaults.getResponseTimeout());
    assertThat(t).extracting(BatchOptions::getRetryAttempts).isEqualTo(123);
    assertThat(t).extracting(BatchOptions::getRetryInterval).isEqualTo(defaults.getRetryInterval());
  }

  @Test
  void interval() {
    BatchOptions t = Defaults.with(RB5.class.getAnnotation(RedisBatched.class));
    BatchOptions defaults = Defaults.create();
    assertThat(t)
        .extracting(BatchOptions::getResponseTimeout)
        .isEqualTo(defaults.getResponseTimeout());
    assertThat(t).extracting(BatchOptions::getRetryAttempts).isEqualTo(defaults.getRetryAttempts());
    assertThat(t).extracting(BatchOptions::getRetryInterval).isEqualTo(123L);
  }
}

@RedisBatched()
class RB1 {}

@RedisBatched(bulkSize = 123)
class RB2 {}

@RedisBatched(bulkSize = 123, responseTimeout = 123)
class RB3 {}

@RedisBatched(retryAttempts = 123)
class RB4 {}

@RedisBatched(retryInterval = 123)
class RB5 {}
