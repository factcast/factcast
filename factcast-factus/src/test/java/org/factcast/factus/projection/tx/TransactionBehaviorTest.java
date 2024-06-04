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
package org.factcast.factus.projection.tx;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionBehaviorTest {

  static class Tx {}

  @Mock private @NonNull TransactionAdapter<Tx> adapter;
  @Mock private Tx runningTransaction;
  @InjectMocks private TransactionBehavior underTest;

  @Nested
  class WhenBegining {
    @BeforeEach
    void setup() {
      when(adapter.beginNewTransaction()).thenReturn(runningTransaction);
    }

    @Test
    void failsOnRunningTransaction() {
      underTest.begin();
      Assertions.assertThat(underTest.runningTransaction()).isNotNull();

      assertThatThrownBy(
              () -> {
                underTest.begin();
              })
          .isInstanceOf(TransactionAlreadyRunningException.class);
    }

    @Test
    void beginsTransaction() {
      Assertions.assertThat(underTest.runningTransaction()).isNull();
      underTest.begin();
      Assertions.assertThat(underTest.runningTransaction()).isSameAs(runningTransaction);
    }
  }

  @Nested
  class WhenCommitting {
    @BeforeEach
    void setup() {}

    @Test
    void failsOnNotRunningTransaction() {
      assertThatThrownBy(
              () -> {
                underTest.commit();
              })
          .isInstanceOf(TransactionNotRunningException.class);
    }

    @Test
    void commitsTransaction() {
      when(adapter.beginNewTransaction()).thenReturn(runningTransaction);

      Assertions.assertThat(underTest.runningTransaction()).isNull();
      underTest.begin();
      Assertions.assertThat(underTest.runningTransaction()).isSameAs(runningTransaction);
      underTest.commit();
      Assertions.assertThat(underTest.runningTransaction()).isNull();
      verify(adapter).commit(runningTransaction);
    }
  }

  @Nested
  class WhenRollbacking {
    @BeforeEach
    void setup() {}

    @Test
    void failsOnNotRunningTransaction() {
      assertThatThrownBy(
              () -> {
                underTest.rollback();
              })
          .isInstanceOf(TransactionNotRunningException.class);
    }

    @Test
    void commitsTransaction() {
      when(adapter.beginNewTransaction()).thenReturn(runningTransaction);

      Assertions.assertThat(underTest.runningTransaction()).isNull();
      underTest.begin();
      Assertions.assertThat(underTest.runningTransaction()).isSameAs(runningTransaction);
      underTest.rollback();
      Assertions.assertThat(underTest.runningTransaction()).isNull();
      verify(adapter).rollback(runningTransaction);
    }
  }

  @Nested
  class WhenAssertingNoRunningTransaction {
    @BeforeEach
    void setup() {}

    @Test
    void passes() {
      underTest.assertNoRunningTransaction();
    }

    @Test
    void fails() {
      when(adapter.beginNewTransaction()).thenReturn(runningTransaction);
      underTest.begin();

      assertThatThrownBy(
              () -> {
                underTest.assertNoRunningTransaction();
              })
          .isInstanceOf(Exception.class);
    }
  }

  @Nested
  class WhenAssertingInTransaction {
    @BeforeEach
    void setup() {}

    @Test
    void passes() {
      when(adapter.beginNewTransaction()).thenReturn(runningTransaction);
      underTest.begin();
      underTest.assertInTransaction();
    }

    @Test
    void fails() {
      assertThatThrownBy(
              () -> {
                underTest.assertInTransaction();
              })
          .isInstanceOf(Exception.class);
    }
  }

  @Nested
  class WhenIningTransaction {
    @BeforeEach
    void setup() {}

    @Test
    void t() {
      when(adapter.beginNewTransaction()).thenReturn(runningTransaction);
      underTest.begin();
      Assertions.assertThat(underTest.inTransaction()).isTrue();
    }

    @Test
    void f() {
      Assertions.assertThat(underTest.inTransaction()).isFalse();
    }
  }

  @Nested
  class WhenMaxingBatchSizePerTransaction {
    @BeforeEach
    void setup() {}

    @Test
    void delegates() {
      int n = 72;
      when(adapter.maxBatchSizePerTransaction()).thenReturn(n);
      Assertions.assertThat(underTest.maxBatchSizePerTransaction()).isEqualTo(n);
    }
  }
}
