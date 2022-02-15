package org.factcast.factus.dynamodb.tx;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import java.util.function.Consumer;
import java.util.function.Function;
import org.factcast.factus.dynamodb.DynamoDBTransaction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DynamoDBTxManagerTest {

  @Mock private AmazonDynamoDB dynamoDB;
  @Mock DynamoDBTransaction tx;
  @InjectMocks private DynamoDBTxManager underTest;

  @Nested
  class WhenGetting {
    @Test
    void noCurrentIfUnstarted() {
      assertThat(underTest.getCurrentTransaction()).isNull();
    }

    @Test
    void currentIfStarted() {
      underTest.startOrJoin();
      assertThat(underTest.getCurrentTransaction()).isNotNull();
    }
  }

  @Nested
  class WhenCommiting {

    @Test
    void commits() {
      underTest = spy(underTest);
      when(underTest.createNewTransaction()).thenReturn(tx);

      underTest.startOrJoin();
      underTest.commit();

      verify(tx).asTransactWriteItemsRequest();
      verify(dynamoDB).transactWriteItems(any());

      // should be null after commit
      assertThat(underTest.getCurrentTransaction()).isNull();
    }

    @Test
    void exceptionOnCommit_is_passed_along() {
      underTest = spy(underTest);
      when(underTest.createNewTransaction()).thenReturn(tx);

      when(dynamoDB.transactWriteItems(any())).thenThrow(IllegalStateException.class);
      underTest.startOrJoin();

      assertThatThrownBy(() -> underTest.commit()).isInstanceOf(IllegalStateException.class);

      verify(tx).asTransactWriteItemsRequest();
      // should be null now
      assertThat(underTest.getCurrentTransaction()).isNull();
    }

    @Test
    void skipsIfNoTxRunning() {
      underTest.commit();
      verifyNoInteractions(dynamoDB);
    }
  }

  @Nested
  class WhenRollingBack {

    @Test
    void joins() {
      underTest = spy(underTest);
      when(underTest.createNewTransaction()).thenReturn(tx);
      underTest.startOrJoin();

      underTest.rollback();

      verify(tx).rollback();
      assertThat(underTest.getCurrentTransaction()).isNull();
    }

    @Test
    void rollback_exception() {
      underTest = spy(underTest);
      when(underTest.createNewTransaction()).thenReturn(tx);
      doThrow(new IllegalStateException("foo")).when(tx).rollback();
      underTest.startOrJoin();

      assertThatThrownBy(() -> underTest.rollback()).isInstanceOf(IllegalStateException.class);

      verify(tx).rollback();
      assertThat(underTest.getCurrentTransaction()).isNull();
    }

    @Test
    void skipsIfNoTxRunning() {
      underTest.rollback();
      verifyNoInteractions(dynamoDB);
    }
  }

  @Nested
  class WhenJoining {
    @Mock private Consumer<DynamoDBTransaction> block;

    @Test
    void startsIfNecessary() {
      underTest.join(
          tx -> {
            assertThat(tx).isNotNull();
          });
    }

    @Test
    void keepsCurrent() {
      underTest.startOrJoin();
      DynamoDBTransaction curr = underTest.getCurrentTransaction();
      underTest.join(
          tx -> {
            assertThat(tx).isSameAs(curr);
          });
    }
  }

  @Nested
  class WhenJoiningFn {
    @Mock private Function<DynamoDBTransaction, ?> block;

    @Test
    void startsIfNecessary() {
      underTest.join(
          tx -> {
            assertThat(tx).isNotNull();
            return 0;
          });
    }

    @Test
    void keepsCurrent() {
      underTest.startOrJoin();
      DynamoDBTransaction curr = underTest.getCurrentTransaction();
      underTest.join(
          tx -> {
            assertThat(tx).isSameAs(curr);
            return 0;
          });
    }
  }

  @Nested
  class WhenStartingOrJoin {

    @Test
    void createsIfNull() {
      underTest = spy(underTest);
      when(underTest.createNewTransaction()).thenReturn(tx);

      assertThat(underTest.getCurrentTransaction()).isNull();
      assertThat(underTest.startOrJoin()).isEqualTo(true);
      assertThat(underTest.getCurrentTransaction()).isSameAs(tx);
    }

    @Test
    void returnsIfNotNull() {
      underTest = spy(underTest);
      when(underTest.createNewTransaction()).thenReturn(tx);

      // first time, it should create
      assertThat(underTest.getCurrentTransaction()).isNull();
      assertThat(underTest.startOrJoin()).isEqualTo(true);
      assertThat(underTest.getCurrentTransaction()).isSameAs(tx);
      verify(underTest).createNewTransaction();

      assertThat(underTest.startOrJoin()).isEqualTo(false);
      assertThat(underTest.getCurrentTransaction()).isSameAs(tx);
      assertThat(underTest.startOrJoin()).isEqualTo(false);
      assertThat(underTest.getCurrentTransaction()).isSameAs(tx);

      verify(underTest, times(1)).createNewTransaction();
    }
  }
}
