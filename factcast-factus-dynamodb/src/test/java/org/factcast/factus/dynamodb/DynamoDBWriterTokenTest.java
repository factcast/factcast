package org.factcast.factus.dynamodb;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Timer;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DynamoDBWriterTokenTest {
  final Timer timer = mock(Timer.class);
  final String lockId = "fooLock";

  @Nested
  class WhenConstructing {
    @Test
    void startsScheduler() {
      DynamoDBOperations ops = mock(DynamoDBOperations.class);
      final DynamoDBWriterToken uut = new DynamoDBWriterToken(ops, lockId, timer);

      assertThat(uut.isValid()).isTrue();
      verify(timer).scheduleAtFixedRate(any(), anyLong(), eq(DynamoDBConstants.DEFAULT_TIMEOUT));
    }
  }

  @Nested
  class WhenClosing {
    @Test
    void testClose() throws Exception {
      DynamoDBOperations ops = mock(DynamoDBOperations.class);
      final DynamoDBWriterToken uut = new DynamoDBWriterToken(ops, lockId, timer);

      uut.close();

      verify(ops).unLock(lockId);
      verify(timer).cancel();
    }
  }

  @Nested
  class WhenCheckingIfIsValid {
    @SneakyThrows
    @Test
    void testIsNoLongerValidAfterClose() {
      DynamoDBOperations ops = mock(DynamoDBOperations.class);
      final DynamoDBWriterToken uut = new DynamoDBWriterToken(ops, lockId, timer);

      assertThat(uut.isValid()).isTrue();
      uut.close();
      assertThat(uut.isValid()).isFalse();
      verify(timer).cancel();
    }
  }
}
