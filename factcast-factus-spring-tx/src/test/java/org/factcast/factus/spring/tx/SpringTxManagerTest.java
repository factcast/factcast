package org.factcast.factus.spring.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import lombok.NonNull;
import lombok.val;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

      val tx = uut.getCurrentTx();

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
      return this.currentTx;
    }
  }
}
