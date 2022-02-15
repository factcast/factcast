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
package org.factcast.factus.dynamodb.tx;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.factus.dynamodb.ADynamoDBManagedProjection;
import org.factcast.factus.dynamodb.DynamoDBManagedProjection;
import org.factcast.factus.dynamodb.DynamoDBTransaction;
import org.factcast.factus.projection.WriterToken;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DynamoDBTransactionalLensTest {
  @Mock private AmazonDynamoDB client;

  @Nested
  class WhenBeforingFactProcessing {
    @Mock private Fact f;

    @BeforeEach
    void setup() {}

    @Test
    void resetsTimeIfBatching() {
      DynamoDBManagedProjection p = new ADynamoDBManagedProjection(client);
      DynamoDBTransactionalLens underTest = new DynamoDBTransactionalLens(p, client);

      underTest.bulkSize(100);
      underTest.start().set(0L);

      underTest.beforeFactProcessing(f);

      assertThat(underTest.start().get())
          .isNotEqualTo(0L)
          .isLessThanOrEqualTo(System.currentTimeMillis())
          .isGreaterThan(System.currentTimeMillis() - 1000);
    }
  }

  @Nested
  class WhenAfteringFactProcessing {
    @Mock private Fact f;

    @BeforeEach
    void setup() {}

    @Test
    void counts() {
      DynamoDBManagedProjection p = new ADynamoDBManagedProjection(client);
      DynamoDBTransactionalLens underTest = spy(new DynamoDBTransactionalLens(p, client));

      when(underTest.shouldFlush()).thenReturn(false);

      int before = underTest.count().get();
      underTest.afterFactProcessing(f);

      assertThat(underTest.count().get()).isEqualTo(before + 1);
    }
  }
  //
  //  @Nested
  //  class WhenOningCatchup {
  //    @Mock private Projection p;
  //
  //    @BeforeEach
  //    void setup() {}
  //
  //    @Test
  //    void doesNotUnnecessarilyflush() {
  //
  //      RedisManagedProjection p = new ARedisTransactionalManagedProjection(client);
  //      RedisTransactionalLens underTest = spy(new RedisTransactionalLens(p, client));
  //
  //      underTest.onCatchup(p);
  //
  //      verify(underTest, never()).flush();
  //    }
  //
  //    @Test
  //    void flushesOnCacthupIfNecessary() {
  //
  //      RedisManagedProjection p = new ARedisTransactionalManagedProjection(client);
  //      RedisTransactionalLens underTest = spy(new RedisTransactionalLens(p, client));
  //
  //      // mark it dirty
  //      underTest.afterFactProcessing(Fact.builder().id(UUID.randomUUID()).buildWithoutPayload());
  //
  //      underTest.onCatchup(p);
  //
  //      verify(underTest, times(1)).flush();
  //    }
  //
  //    @Test
  //    void disablesBatching() {
  //
  //      RedisManagedProjection p = new ARedisTransactionalManagedProjection(client);
  //      RedisTransactionalLens underTest = spy(new RedisTransactionalLens(p, client));
  //      assertThat(underTest.bulkSize()).isNotEqualTo(1);
  //      underTest.onCatchup(p);
  //
  //      assertThat(underTest.bulkSize()).isEqualTo(1);
  //    }
  //  }
  //
  @Nested
  class WhenSkipingStateUpdate {
    @BeforeEach
    void setup() {}

    @Test
    void calculatesStateSkipping() {

      DynamoDBManagedProjection p = new ADynamoDBManagedProjection(client);
      DynamoDBTransactionalLens underTest = spy(new DynamoDBTransactionalLens(p, client));

      when(underTest.shouldFlush(anyBoolean())).thenReturn(false, false, true, true);
      when(underTest.isBulkApplying()).thenReturn(false, true, false, true);

      assertThat(underTest.skipStateUpdate()).isFalse();
      assertThat(underTest.skipStateUpdate()).isTrue();
      assertThat(underTest.skipStateUpdate()).isFalse();
      assertThat(underTest.skipStateUpdate()).isTrue();
    }
  }

  @Nested
  class WhenFlushing {
    @BeforeEach
    void setup() {}

    @Test
    void resetsClock() {
      DynamoDBManagedProjection p = new ADynamoDBManagedProjection(client);
      DynamoDBTransactionalLens underTest = spy(new DynamoDBTransactionalLens(p, client));

      underTest.start().set(System.currentTimeMillis());

      underTest.flush();

      assertThat(underTest.start().get()).isEqualTo(0);
    }

    @Test
    void delegates() {
      DynamoDBManagedProjection p = new ADynamoDBManagedProjection(client);
      DynamoDBTransactionalLens underTest = spy(new DynamoDBTransactionalLens(p, client));

      underTest.flush();
      verify(underTest).doFlush();
    }
  }

  @Nested
  class WhenDoingClear {
    @BeforeEach
    void setup() {}

    @Test
    void delegates() {

      try (MockedStatic<DynamoDBTxManager> manager = Mockito.mockStatic(DynamoDBTxManager.class)) {
        DynamoDBTxManager mgr = mock(DynamoDBTxManager.class);
        manager.when(() -> DynamoDBTxManager.get(same(client))).thenReturn(mgr);

        DynamoDBManagedProjection p = new ADynamoDBManagedProjection(client);
        DynamoDBTransactionalLens underTest = new DynamoDBTransactionalLens(p, client);

        underTest.doClear();

        verify(mgr).rollback();
      }
    }
  }

  @Nested
  class WhenDoingFlush {
    @BeforeEach
    void setup() {}

    @Test
    void delegates() {
      try (MockedStatic<DynamoDBTxManager> manager = Mockito.mockStatic(DynamoDBTxManager.class)) {
        DynamoDBTxManager mgr = mock(DynamoDBTxManager.class);
        manager.when(() -> DynamoDBTxManager.get(same(client))).thenReturn(mgr);

        DynamoDBManagedProjection p = new ADynamoDBManagedProjection(client);
        DynamoDBTransactionalLens underTest = new DynamoDBTransactionalLens(p, client);

        underTest.doFlush();

        verify(mgr).commit();
      }
    }
  }

  @Nested
  class WhenAfteringFactProcessingFailed {
    @Mock private Fact f;
    @Mock private Throwable justForInformation;

    @BeforeEach
    void setup() {}

    @Test
    void rollsback() {
      DynamoDBManagedProjection p = new ADynamoDBManagedProjection(client);
      DynamoDBTransactionalLens underTest = spy(new DynamoDBTransactionalLens(p, client));

      underTest.afterFactProcessingFailed(f, new IOException("oh dear"));

      verify(underTest).doClear();
    }
  }

  @Nested
  class WhenGettingSize {

    @Test
    void failsOnNoAnnotation() {
      assertThatThrownBy(
              () -> {
                DynamoDBTransactionalLens.getSize(new NonAnnotatedRedisManagedProjection());
              })
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class WhenParameteringTransformerFor {

    @Mock private Fact f;
    @Mock DynamoDBTransaction current;

    @Test
    void returnsCurrentTx() {
      try (MockedStatic<DynamoDBTxManager> manager = Mockito.mockStatic(DynamoDBTxManager.class)) {
        DynamoDBTxManager mgr = mock(DynamoDBTxManager.class);

        when(mgr.getCurrentTransaction()).thenReturn(current);
        manager.when(() -> DynamoDBTxManager.get(same(client))).thenReturn(mgr);

        DynamoDBManagedProjection p = new ADynamoDBManagedProjection(client);
        var underTest = new DynamoDBTransactionalLens(p, client);
        Function<Fact, ?> t = underTest.parameterTransformerFor(DynamoDBTransaction.class);
        assertThat(t).isNotNull();
        assertThat(t.apply(f))
            .isInstanceOf(DynamoDBTransaction.class)
            .isNotNull()
            .isSameAs(current);
      }
    }

    class SomeUnrelatedType {}

    @Test
    void returnsNullForOtherType() {
      try (MockedStatic<DynamoDBTxManager> manager = Mockito.mockStatic(DynamoDBTxManager.class)) {
        DynamoDBTxManager mgr = mock(DynamoDBTxManager.class);
        manager.when(() -> DynamoDBTxManager.get(same(client))).thenReturn(mgr);

        DynamoDBManagedProjection p = new ADynamoDBManagedProjection(client);
        var underTest = new DynamoDBTransactionalLens(p, client);

        Function<Fact, ?> t = underTest.parameterTransformerFor(SomeUnrelatedType.class);
        assertThat(t).isNull();
      }
    }
  }

  class NonAnnotatedRedisManagedProjection implements DynamoDBManagedProjection {

    @Override
    public UUID factStreamPosition() {
      return null;
    }

    @Override
    public void factStreamPosition(@NonNull UUID factStreamPosition) {}

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      return null;
    }

    @Override
    public @NonNull AmazonDynamoDB dynamoDB() {
      return null;
    }
  }
}
