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
package org.factcast.factus.redis.batch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class RedissonBatchManagerTest {

  @Mock private RedissonClient redisson;
  @InjectMocks private RedissonBatchManager underTest;

  @Nested
  class WhenGetting {
    @Test
    void noCurrentIfUnstarted() {
      assertThat(underTest.getCurrentBatch()).isNull();
    }

    @Test
    void currentIfStarted() {
      underTest.startOrJoin();
      assertThat(underTest.getCurrentBatch()).isNull();
    }
  }

  @Nested
  class WhenCommiting {
    @Mock RBatch batch;

    @Test
    void joins() {
      when(redisson.createBatch(any())).thenReturn(batch);
      underTest.startOrJoin();

      underTest.execute();

      verify(batch).execute();
      assertThat(underTest.getCurrentBatch()).isNull();
    }

    @Test
    void execute_exception() {
      when(redisson.createBatch(any())).thenReturn(batch);
      doThrow(new IllegalStateException("foo")).when(batch).execute();
      underTest.startOrJoin();

      assertThatThrownBy(() -> underTest.execute()).isInstanceOf(IllegalStateException.class);

      verify(batch).execute();
      assertThat(underTest.getCurrentBatch()).isNull();
    }

    @Test
    void skipsIfNoTxRunning() {
      RBatch curr = underTest.getCurrentBatch();
      assertThat(curr).isNull();
      underTest.execute();

      // no asserttion, does not throw exception, thats it
    }
  }

  @Nested
  class WhenRollingBack {
    @Mock RBatch batch;

    @Test
    void joins() {
      when(redisson.createBatch(any())).thenReturn(batch);
      underTest.startOrJoin();

      underTest.discard();

      verify(batch).discard();
      assertThat(underTest.getCurrentBatch()).isNull();
    }

    @Test
    void discard_exception() {
      when(redisson.createBatch(any())).thenReturn(batch);
      doThrow(new IllegalStateException("foo")).when(batch).discard();
      underTest.startOrJoin();

      assertThatThrownBy(() -> underTest.discard()).isInstanceOf(IllegalStateException.class);

      verify(batch).discard();
      assertThat(underTest.getCurrentBatch()).isNull();
    }

    @Test
    void skipsIfNoTxRunning() {
      RBatch curr = underTest.getCurrentBatch();
      assertThat(curr).isNull();
      underTest.discard();

      // no asserttion, does not throw exception, thats it
    }
  }

  @Nested
  class WhenJoining {

    @BeforeEach
    void setup() {
      when(redisson.createBatch(any())).thenAnswer(i -> mock(RBatch.class));
    }

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
      RBatch curr = underTest.getCurrentBatch();
      underTest.join(
          tx -> {
            assertThat(tx).isSameAs(curr);
          });
    }
  }

  @Nested
  class WhenJoiningFn {

    @BeforeEach
    void setup() {
      when(redisson.createBatch(any())).thenAnswer(i -> mock(RBatch.class));
    }

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
      RBatch curr = underTest.getCurrentBatch();
      underTest.join(
          tx -> {
            assertThat(tx).isSameAs(curr);
            return 0;
          });
    }
  }

  @Nested
  class WhenStartingOrJoin {
    @Mock private RBatch rtx;

    @Test
    void createsIfNull() {
      when(redisson.createBatch(any())).thenReturn(rtx);

      assertThat(underTest.getCurrentBatch()).isNull();
      assertThat(underTest.startOrJoin()).isEqualTo(true);
      assertThat(underTest.getCurrentBatch()).isSameAs(rtx);
    }

    @Test
    void returnsIfNotNull() {
      when(redisson.createBatch(any())).thenReturn(rtx, (RBatch) null);
      // first time, it should create
      assertThat(underTest.getCurrentBatch()).isNull();
      assertThat(underTest.startOrJoin()).isEqualTo(true);
      assertThat(underTest.getCurrentBatch()).isSameAs(rtx);
      verify(redisson).createBatch(any());

      assertThat(underTest.startOrJoin()).isEqualTo(false);
      assertThat(underTest.getCurrentBatch()).isSameAs(rtx);
      assertThat(underTest.startOrJoin()).isEqualTo(false);
      assertThat(underTest.getCurrentBatch()).isSameAs(rtx);

      verifyNoMoreInteractions(redisson);
    }
  }
}
