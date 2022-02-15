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
