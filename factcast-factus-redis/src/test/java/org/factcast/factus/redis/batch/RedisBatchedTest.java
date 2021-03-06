package org.factcast.factus.redis.batch;

import static org.assertj.core.api.Assertions.*;

import lombok.val;
import org.factcast.factus.redis.batch.RedisBatched.Defaults;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
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
    val t = Defaults.with(RB3.class.getAnnotation(RedisBatched.class));
    BatchOptions defaults = Defaults.create();
    assertThat(t.getResponseTimeout()).isEqualTo(123);
    assertThat(t).extracting(BatchOptions::getRetryAttempts).isEqualTo(defaults.getRetryAttempts());
    assertThat(t).extracting(BatchOptions::getRetryInterval).isEqualTo(defaults.getRetryInterval());
  }

  @Test
  void attempts() {
    val t = Defaults.with(RB4.class.getAnnotation(RedisBatched.class));
    BatchOptions defaults = Defaults.create();
    assertThat(t)
        .extracting(BatchOptions::getResponseTimeout)
        .isEqualTo(defaults.getResponseTimeout());
    assertThat(t).extracting(BatchOptions::getRetryAttempts).isEqualTo(123);
    assertThat(t).extracting(BatchOptions::getRetryInterval).isEqualTo(defaults.getRetryInterval());
  }

  @Test
  void interval() {
    val t = Defaults.with(RB5.class.getAnnotation(RedisBatched.class));
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

@RedisBatched(size = 123)
class RB2 {}

@RedisBatched(size = 123, responseTimeout = 123)
class RB3 {}

@RedisBatched(retryAttempts = 123)
class RB4 {}

@RedisBatched(retryInterval = 123)
class RB5 {}
