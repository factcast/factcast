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
package org.factcast.factus.spring.tx;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.projection.tx.TransactionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class SpringTxAdapterTest {

  @SpringTransactional
  static class JustForAnnotation {}

  @Mock private PlatformTransactionManager platformTransactionManager;
  @Mock private TransactionStatus tx;
  private SpringTransactional annotation =
      JustForAnnotation.class.getAnnotation(SpringTransactional.class);
  private SpringTxAdapter underTest;

  @BeforeEach
  void setup() {
    underTest = new SpringTxAdapter(platformTransactionManager, annotation);
  }

  @Nested
  class WhenBeginingNewTransaction {

    @BeforeEach
    void setup() {
      when(platformTransactionManager.getTransaction(any())).thenReturn(tx);
      underTest = spy(underTest);
      when(underTest.transactionOptions()).thenCallRealMethod();
    }

    @Test
    void delegates() {
      Assertions.assertThat(underTest.beginNewTransaction()).isSameAs(tx);
    }
  }

  @Nested
  class WhenTransactioningOptions {

    @Test
    void usesDefaults() {
      // TOptions does not support equals, so we're extracting the state to compare
      Assertions.assertThat(FactCastJson.writeValueAsBytes(underTest.transactionOptions()))
          .isEqualTo(FactCastJson.writeValueAsBytes(SpringTransactional.Defaults.create()));
    }

    @Test
    void overridesOptions() {

      underTest =
          new SpringTxAdapter(
              platformTransactionManager, Timeout12.class.getAnnotation(SpringTransactional.class));

      // TOptions does not support equals, so we're extracting the state to compare
      Assertions.assertThat(FactCastJson.writeValueAsBytes(underTest.transactionOptions()))
          .isNotEqualTo(FactCastJson.writeValueAsBytes(SpringTransactional.Defaults.create()));
      Assertions.assertThat(underTest.transactionOptions().getTimeout()).isEqualTo(12);
    }

    @SpringTransactional(timeoutInSeconds = 12)
    class Timeout12 {}
  }

  @Nested
  class WhenCommitting {
    @Mock private @NonNull TransactionStatus runningTransaction;

    @BeforeEach
    void setup() {
      when(platformTransactionManager.getTransaction(any())).thenReturn(tx);
    }

    @Test
    void delegates() {
      tx = underTest.beginNewTransaction();
      underTest.commit(tx);
      verify(platformTransactionManager).commit(tx);
    }
  }

  @Nested
  class WhenRollbacking {
    @Mock private @NonNull TransactionStatus runningTransaction;

    @BeforeEach
    void setup() {
      when(platformTransactionManager.getTransaction(any())).thenReturn(tx);
    }

    @Test
    void delegates() {
      tx = underTest.beginNewTransaction();
      underTest.rollback(tx);
      verify(platformTransactionManager).rollback(tx);
    }
  }

  @Nested
  class WhenMaxingBatchSizePerTransaction {
    @BeforeEach
    void setup() {}

    @Test
    void readAnnotation() {
      underTest =
          new SpringTxAdapter(
              platformTransactionManager, Bulk31.class.getAnnotation(SpringTransactional.class));

      // TOptions does not support equals, so we're extracting the state to compare
      Assertions.assertThat(FactCastJson.writeValueAsBytes(underTest.transactionOptions()))
          .isEqualTo(FactCastJson.writeValueAsBytes(SpringTransactional.Defaults.create()));
      Assertions.assertThat(underTest.maxBatchSizePerTransaction()).isEqualTo(31);
    }

    @Test
    void usesDefault() {
      underTest = new SpringTxAdapter(platformTransactionManager, null);
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

    @SpringTransactional(bulkSize = 31)
    class Bulk31 {}
  }
}
