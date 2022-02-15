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
package org.factcast.factus.spring.tx;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import lombok.NonNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class SpringTxManagerTest {

  @Mock private @NonNull PlatformTransactionManager transactionManager;
  @Mock private @NonNull TransactionDefinition definition;
  @Mock private TransactionStatus status;

  @InjectMocks private SpringTxManagerImpl uut;

  @Nested
  class WhenStartingOrJoin {

    @Test
    void testStartOrJoin() {
      when(transactionManager.getTransaction(definition)).thenReturn(status);

      assertThat(uut.getCurrentTx()).isNull();

      uut.startOrJoin();

      assertThat(uut.getCurrentTx()).isNotNull();

      TransactionStatus tx = uut.getCurrentTx();

      uut.startOrJoin();

      assertThat(uut.getCurrentTx()).isEqualTo(tx);

      verify(transactionManager, times(1)).getTransaction(definition);
    }
  }

  @Nested
  class WhenCommitting {
    @Test
    void testCommit_happyCase() {
      when(transactionManager.getTransaction(definition)).thenReturn(status);

      uut.startOrJoin();

      assertThat(uut.getCurrentTx()).isNotNull();

      uut.commit();

      assertThat(uut.getCurrentTx()).isNull();

      verify(transactionManager, times(1)).getTransaction(definition);
      verify(transactionManager).commit(status);
    }

    @Test
    void testCommit_happyCase_exception() {
      when(transactionManager.getTransaction(definition)).thenReturn(status);
      doThrow(new IllegalStateException("foo")).when(transactionManager).commit(status);

      uut.startOrJoin();

      assertThat(uut.getCurrentTx()).isNotNull();

      assertThatThrownBy(() -> uut.commit()).isInstanceOf(IllegalStateException.class);

      assertThat(uut.getCurrentTx()).isNull();

      verify(transactionManager, times(1)).getTransaction(definition);
      verify(transactionManager).commit(status);
    }

    @Test
    void testCommit_noTx() {
      uut.commit();

      verifyNoInteractions(transactionManager);
    }
  }

  @Nested
  class WhenRollbacking {
    @Test
    void testRollback_happyCase() {
      when(transactionManager.getTransaction(definition)).thenReturn(status);

      uut.startOrJoin();

      assertThat(uut.getCurrentTx()).isNotNull();

      uut.rollback();

      assertThat(uut.getCurrentTx()).isNull();

      verify(transactionManager, times(1)).getTransaction(definition);
      verify(transactionManager).rollback(status);
    }

    @Test
    void testRollback_happyCase_exception() {
      when(transactionManager.getTransaction(definition)).thenReturn(status);
      doThrow(new IllegalStateException("foo")).when(transactionManager).rollback(status);

      uut.startOrJoin();

      assertThat(uut.getCurrentTx()).isNotNull();

      assertThatThrownBy(() -> uut.rollback()).isInstanceOf(IllegalStateException.class);

      assertThat(uut.getCurrentTx()).isNull();

      verify(transactionManager, times(1)).getTransaction(definition);
      verify(transactionManager).rollback(status);
    }

    @Test
    void testRollback_noTx() {
      uut.rollback();

      verifyNoInteractions(transactionManager);
    }
  }

  static class SpringTxManagerImpl extends SpringTxManager {

    public SpringTxManagerImpl(
        @NonNull PlatformTransactionManager transactionManager,
        @NonNull TransactionDefinition definition) {
      super(transactionManager, definition);
    }

    public TransactionStatus getCurrentTx() {
      return currentTx;
    }
  }
}
