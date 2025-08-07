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
package org.factcast.store.internal;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import com.google.common.base.Stopwatch;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import lombok.*;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.factcast.core.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.*;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.tail.MemoizedFastForwardTarget;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
class PgFactBenchmark {

  static {
    System.setProperty(
        "factcast.store.concurrencyStrategy",
        StoreConfigurationProperties.PgConcurrencyStrategy.LEGACY.name());
  }

  @Autowired FactStore fs;

  @Autowired PgMetrics metrics;

  @Autowired TokenStore tokenStore;

  @Autowired MemoizedFastForwardTarget fastForwardTarget;

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired private PgSubscriptionFactory pgSubscriptionFactory;
  @Autowired private SchemaRegistry schemaRegistry;
  @Autowired private StoreConfigurationProperties props;

  @Nested
  @Disabled
  class ConcurrentInsertBenchmark {
    private ExecutorService es;
    private AtomicLong retries = new AtomicLong();
    private Collection<Long> dataPoints = new LinkedList<>();

    @BeforeEach
    void setUp() {
      es = Executors.newFixedThreadPool(64);
    }

    @Test
    void irre() {
      Fact f = dummyFact();
      {
        fs.publish(Collections.singletonList(f));

        StateToken stateToken =
            fs.stateFor(Collections.singletonList(FactSpec.ns(f.ns()).type(f.type())));
        Optional<State> state = tokenStore.get(stateToken);
        if (state.get().serialOfLastMatchingFact() == 0) throw new IllegalStateException();

        System.out.println(state.get().serialOfLastMatchingFact());
      }

      {
        StateToken stateToken =
            fs.stateFor(Collections.singletonList(FactSpec.ns(f.ns()).type(f.type())));

        List<Fact> l = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
          l.add(dummyFact());
        }
        // should work as nothing has happened in between
        Assertions.assertThat(fs.publishIfUnchanged(l, Optional.of(stateToken))).isTrue();
        Assertions.assertThat(
                fs.publishIfUnchanged(
                    Collections.singletonList(dummyFact()), Optional.of(stateToken)))
            .isFalse();
      }
    }

    @Test
    @SneakyThrows
    void testSerialized() {

      Fact f = dummyFact();
      fs.publish(Collections.singletonList(f));
      {
        StateToken stateToken =
            fs.stateFor(Collections.singletonList(FactSpec.ns(f.ns()).type(f.type())));
        Optional<State> state = tokenStore.get(stateToken);
        if (state.get().serialOfLastMatchingFact() == 0) throw new IllegalStateException();
      }
      ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.factcast"))
          .setLevel(Level.INFO);

      Stopwatch stopwatch = Stopwatch.createStarted();
      for (int i = 0; i < 10000; i++) {
        enqueue();
      }

      es.shutdown();

      ThreadPoolExecutor e = (ThreadPoolExecutor) es;

      while (e.isTerminating()) {
        long compl = e.getCompletedTaskCount();
        Thread.sleep(1000);
        long perSecond = e.getCompletedTaskCount() - compl;
        if (e.isTerminating()) {
          dataPoints.add(perSecond);
          System.out.println(
              String.format(
                  "progress: active: %s, completed: %s, perSecond: %s",
                  e.getActiveCount(), e.getCompletedTaskCount(), perSecond));
        }
      }

      System.out.println(
          String.format(
              "rt %s ms, retries %s, avg per second: %s",
              stopwatch.stop().elapsed(TimeUnit.MILLISECONDS),
              retries.get(),
              dataPoints.stream().mapToLong(Long::longValue).average().getAsDouble()));
    }

    static AtomicLong count = new AtomicLong();

    void enqueue() {
      es.submit(this::singleUnconditionalInsert);
      es.submit(this::singleUnconditionalInsert);
      es.submit(this::singleUnconditionalInsert);
      es.submit(this::singleUnconditionalInsert);
      es.submit(this::singleUnconditionalInsert);
      es.submit(this::singleUnconditionalInsert);
      es.submit(this::singleUnconditionalInsert);
      es.submit(this::singleUnconditionalInsert);

      es.submit(this::singleUnconditionalInsert);
      es.submit(this::singleUnconditionalInsert);
    }

    private void singleConditionalInsert() {
      Fact f = dummyFact();
      @NonNull List<? extends Fact> facts = Collections.singletonList(f);
      @NonNull List<FactSpec> specs = Lists.newArrayList(FactSpec.ns(f.ns()).type(f.type()));
      while (true) {
        StateToken token = fs.stateFor(specs);
        // now the client executes some business logic and comes back a while later,
        // we'll add some latency here to make it more realistic
        sleep(20);
        if (!fs.publishIfUnchanged(facts, Optional.of(token))) {
          retries.incrementAndGet();
        } else break;
      }
      ;
    }

    private static void sleep(long ms) {
      try {
        Thread.sleep(ms);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    private Fact dummyFact() {
      // 5% conflict likeability
      String type = "type" + count.incrementAndGet();
      return Fact.builder().ns("default").type(type).build("{}");
    }

    private void singleUnconditionalInsert() {
      fs.publish(Collections.singletonList(dummyFact()));
    }
  }
}
