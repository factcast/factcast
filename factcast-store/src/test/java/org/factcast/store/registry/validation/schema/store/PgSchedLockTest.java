/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.registry.validation.schema.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {PgTestConfiguration.class, PgSchedLockTestConfiguration.class})
@ExtendWith(SpringExtension.class)
@IntegrationTest
public class PgSchedLockTest {
  @Autowired LockProvider lockProvider;

  static final CountDownLatch latch = new CountDownLatch(1);

  static AtomicReference<SqlRowSet> rs = new AtomicReference<>();

  @Test
  public void schedLockEffectiveWithScheduledMethod() throws InterruptedException {

    // wait for the intercepted bean to finish it's work (and release the lock

    latch.await(5, TimeUnit.SECONDS);

    // and make sure it had one when executing the scheduled method
    assertTrue(rs.get().next(), "Lock should have been found");
  }

  @Test
  @SneakyThrows
  public void extendsLock() {
    final var lock = lockProvider.lock(buildLockConfig());

    assertThat(lock.isPresent()).isTrue();

    // now we have to sleep for at least 30s, better 45s
    Thread.sleep(Duration.ofSeconds(45).toMillis());

    // this lock should still be held by the first one, so no new lock
    assertThat(lockProvider.lock(buildLockConfig()).isEmpty()).isTrue();

    lock.get().unlock();
  }

  private static @NonNull LockConfiguration buildLockConfig() {
    return new LockConfiguration(
        Instant.now(),
        "hugo",
        Duration.ofSeconds(30), // we cannot go lower than 30s here because this is the min for
        // KeepAliveLockProvider
        Duration.ofSeconds(2));
  }
}

class PgSchedLockTestConfiguration {
  @Bean
  public BeanToIntercept bean(JdbcTemplate tpl) {
    return new BeanToIntercept(tpl);
  }
}

@RequiredArgsConstructor
@Slf4j
class BeanToIntercept {
  final JdbcTemplate tpl;

  @Scheduled(initialDelay = 1000, fixedDelay = 1000)
  @SchedulerLock(name = "hubba")
  public void scheduled() throws InterruptedException {
    log.info("hubba");

    PgSchedLockTest.rs.set(tpl.queryForRowSet("select * from shedlock"));
    PgSchedLockTest.latch.countDown();
  }
}
