/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.factus.redis.tx;

import static org.mockito.Mockito.*;

import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.projection.tx.TransactionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;

@ExtendWith(MockitoExtension.class)
class RedisTransactionAdapterTest {

  @Mock private @NonNull RedissonClient client;
  @Mock private RedisTransactional annotation;
  @Mock private RTransaction tx;
  @Mock private TransactionOptions opt;
  @InjectMocks private RedisTransactionAdapter underTest;

  @Nested
  class WhenBeginingNewTransaction {

    @BeforeEach
    void setup() {
      when(client.createTransaction(any())).thenReturn(tx);
      underTest = spy(underTest);
      when(underTest.transactionOptions()).thenReturn(opt);
    }

    @Test
    void delegates() {
      Assertions.assertThat(underTest.beginNewTransaction()).isSameAs(tx);
      verify(client).createTransaction(opt);
    }
  }

  @Nested
  class WhenTransactioningOptions {

    @Test
    void usesDefaults() {
      // TOptions does not support equals, so we're extracting the state to compare
      Assertions.assertThat(FactCastJson.writeValueAsBytes(underTest.transactionOptions()))
          .isEqualTo(FactCastJson.writeValueAsBytes(RedisTransactional.Defaults.create()));
    }

    @Test
    void overridesOptions() {

      underTest =
          new RedisTransactionAdapter(
              client, Timeout12.class.getAnnotation(RedisTransactional.class));

      // TOptions does not support equals, so we're extracting the state to compare
      Assertions.assertThat(FactCastJson.writeValueAsBytes(underTest.transactionOptions()))
          .isNotEqualTo(FactCastJson.writeValueAsBytes(RedisTransactional.Defaults.create()));
      Assertions.assertThat(underTest.transactionOptions().getTimeout()).isEqualTo(12);
    }

    @RedisTransactional(timeout = 12)
    class Timeout12 {}
  }

  @Nested
  class WhenCommitting {
    @Mock private @NonNull RTransaction runningTransaction;

    @BeforeEach
    void setup() {
      when(client.createTransaction(any())).thenReturn(tx);
    }

    @Test
    void delegates() {
      tx = underTest.beginNewTransaction();
      underTest.commit(tx);
      verify(tx).commit();
    }
  }

  @Nested
  class WhenRollbacking {
    @Mock private @NonNull RTransaction runningTransaction;

    @BeforeEach
    void setup() {
      when(client.createTransaction(any())).thenReturn(tx);
    }

    @Test
    void delegates() {
      tx = underTest.beginNewTransaction();
      underTest.rollback(tx);
      verify(tx).rollback();
    }
  }

  @Nested
  class WhenMaxingBatchSizePerTransaction {
    @BeforeEach
    void setup() {}

    @Test
    void readAnnotation() {
      underTest =
          new RedisTransactionAdapter(client, Bulk31.class.getAnnotation(RedisTransactional.class));

      // TOptions does not support equals, so we're extracting the state to compare
      Assertions.assertThat(FactCastJson.writeValueAsBytes(underTest.transactionOptions()))
          .isEqualTo(FactCastJson.writeValueAsBytes(RedisTransactional.Defaults.create()));
      Assertions.assertThat(underTest.maxBatchSizePerTransaction()).isEqualTo(31);
    }

    @Test
    void usesDefault() {
      underTest = new RedisTransactionAdapter(client, null);
      Assertions.assertThat(underTest.maxBatchSizePerTransaction())
          .isEqualTo(
              new TransactionAdapter() {
                @NonNull
                @Override
                public Object beginNewTransaction() {
                  return null;
                }

                @Override
                public void rollback(@NonNull Object runningTransaction) {}

                @Override
                public void commit(@NonNull Object runningTransaction) {}
              }.maxBatchSizePerTransaction());
    }

    @RedisTransactional(bulkSize = 31)
    class Bulk31 {}
  }
}
