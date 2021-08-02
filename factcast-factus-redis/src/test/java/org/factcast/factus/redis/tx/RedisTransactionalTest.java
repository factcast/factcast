package org.factcast.factus.redis.tx;

import static org.assertj.core.api.Assertions.*;

import org.factcast.factus.redis.tx.RedisTransactional.Defaults;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.TransactionOptions;

@ExtendWith(MockitoExtension.class)
public class RedisTransactionalTest {

  @Test
  void defaults() {
    RedisTransactional rb1 = RB1.class.getAnnotation(RedisTransactional.class);
    RedisTransactional rb2 = RB2.class.getAnnotation(RedisTransactional.class);

    TransactionOptions defaults = Defaults.create();
    TransactionOptions withRb1 = Defaults.with(rb1);
    TransactionOptions withRb2 = Defaults.with(rb1);

    assertThat(withRb1).isEqualToComparingFieldByField(defaults);
    assertThat(withRb2).isEqualToComparingFieldByField(defaults);
  }

  @Test
  void responseTimeout() {
    var t = Defaults.with(RB3.class.getAnnotation(RedisTransactional.class));
    TransactionOptions defaults = Defaults.create();
    assertThat(t).extracting(TransactionOptions::getTimeout).isEqualTo(defaults.getTimeout());
    assertThat(t).extracting(TransactionOptions::getResponseTimeout).isEqualTo(123L);
    assertThat(t)
        .extracting(TransactionOptions::getRetryAttempts)
        .isEqualTo(defaults.getRetryAttempts());
    assertThat(t)
        .extracting(TransactionOptions::getRetryInterval)
        .isEqualTo(defaults.getRetryInterval());

    // assertThat(t).extracting(TransactionOptions::getTimeout).isEqualTo(123);
  }

  @Test
  void timeout() {
    var t = Defaults.with(RB6.class.getAnnotation(RedisTransactional.class));
    TransactionOptions defaults = Defaults.create();
    assertThat(t).extracting(TransactionOptions::getTimeout).isEqualTo(123L);
    assertThat(t)
        .extracting(TransactionOptions::getResponseTimeout)
        .isEqualTo(defaults.getResponseTimeout());
    assertThat(t)
        .extracting(TransactionOptions::getRetryAttempts)
        .isEqualTo(defaults.getRetryAttempts());
    assertThat(t)
        .extracting(TransactionOptions::getRetryInterval)
        .isEqualTo(defaults.getRetryInterval());

    // assertThat(t).extracting(TransactionOptions::getTimeout).isEqualTo(123);
  }

  @Test
  void attempts() {
    var t = Defaults.with(RB4.class.getAnnotation(RedisTransactional.class));
    TransactionOptions defaults = Defaults.create();
    assertThat(t).extracting(TransactionOptions::getTimeout).isEqualTo(defaults.getTimeout());
    assertThat(t)
        .extracting(TransactionOptions::getResponseTimeout)
        .isEqualTo(defaults.getResponseTimeout());
    assertThat(t).extracting(TransactionOptions::getRetryAttempts).isEqualTo(123);
    assertThat(t)
        .extracting(TransactionOptions::getRetryInterval)
        .isEqualTo(defaults.getRetryInterval());
  }

  @Test
  void interval() {
    var t = Defaults.with(RB5.class.getAnnotation(RedisTransactional.class));
    TransactionOptions defaults = Defaults.create();
    assertThat(t).extracting(TransactionOptions::getTimeout).isEqualTo(defaults.getTimeout());
    assertThat(t)
        .extracting(TransactionOptions::getResponseTimeout)
        .isEqualTo(defaults.getResponseTimeout());
    assertThat(t)
        .extracting(TransactionOptions::getRetryAttempts)
        .isEqualTo(defaults.getRetryAttempts());
    assertThat(t).extracting(TransactionOptions::getRetryInterval).isEqualTo(123L);
  }
}

@RedisTransactional()
class RB1 {}

@RedisTransactional(size = 123)
class RB2 {}

@RedisTransactional(size = 123, responseTimeout = 123)
class RB3 {}

@RedisTransactional(retryAttempts = 123)
class RB4 {}

@RedisTransactional(retryInterval = 123)
class RB5 {}

@RedisTransactional(timeout = 123)
class RB6 {}
