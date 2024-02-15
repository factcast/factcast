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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import javax.annotation.Nullable;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.FactStreamPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractTransactionAwareProjectionTest {

  @Mock private Tx runningTransaction;
  private TestTransactionAwareProjection underTest = spy(new TestTransactionAwareProjection());

  @Nested
  class WhenBegining {
    @BeforeEach
    void setup() {}

    @Test
    void beginStarts() {
      Assertions.assertThat(underTest.inTransaction()).isFalse();
      underTest.begin();
      Assertions.assertThat(underTest.inTransaction()).isTrue();
    }

    @Test
    void beginDelegates() {
      underTest.begin();
      verify(underTest).beginNewTransaction();
    }

    @Test
    void remembersTransaction() {
      @NonNull Tx t = new Tx();
      when(underTest.beginNewTransaction()).thenReturn(t);
      underTest.begin();
      verify(underTest).beginNewTransaction();
      assertThat(underTest.runningTransaction()).isSameAs(t);
    }

    @Test
    void failureOnBeginDoesNotChangeState() {
      doThrow(RuntimeException.class).when(underTest).beginNewTransaction();

      Assertions.assertThat(underTest.inTransaction()).isFalse();
      assertThatThrownBy(
              () -> {
                underTest.begin();
              })
          .isInstanceOf(Exception.class);
    }
  }

  @Nested
  class WhenCommitting {
    @BeforeEach
    void setup() {}

    @Test
    void commitEnds() {
      Assertions.assertThat(underTest.inTransaction()).isFalse();
      underTest.begin();
      Assertions.assertThat(underTest.inTransaction()).isTrue();
      underTest.commit();
      verify(underTest).commit(any());
      Assertions.assertThat(underTest.inTransaction()).isFalse();
    }

    @Test
    void failingCommitEnds() {

      doThrow(RuntimeException.class).when(underTest).commit(any());

      Assertions.assertThat(underTest.inTransaction()).isFalse();
      underTest.begin();
      Assertions.assertThat(underTest.inTransaction()).isTrue();
      assertThatThrownBy(
              () -> {
                underTest.commit();
              })
          .isInstanceOf(Exception.class);
      verify(underTest).commit(any());
      Assertions.assertThat(underTest.inTransaction()).isFalse();
    }
  }

  @Nested
  class WhenRollbacking {
    @BeforeEach
    void setup() {}

    @Test
    void rollbackEnds() {
      Assertions.assertThat(underTest.inTransaction()).isFalse();
      underTest.begin();
      Assertions.assertThat(underTest.inTransaction()).isTrue();
      underTest.rollback();
      Assertions.assertThat(underTest.inTransaction()).isFalse();
      verify(underTest).rollback(any());
    }

    @Test
    void failinRollbackEnds() {
      doThrow(RuntimeException.class).when(underTest).rollback(any());

      Assertions.assertThat(underTest.inTransaction()).isFalse();
      underTest.begin();
      Assertions.assertThat(underTest.inTransaction()).isTrue();
      assertThatThrownBy(
              () -> {
                underTest.rollback();
              })
          .isInstanceOf(Exception.class);
      Assertions.assertThat(underTest.inTransaction()).isFalse();
      verify(underTest).rollback(any());
    }
  }

  @Nested
  class WhenAssertingNoRunningTransaction {
    @BeforeEach
    void setup() {}

    @Test
    void throwsIfRunning() {
      underTest.begin();
      assertThatThrownBy(
              () -> {
                underTest.assertNoRunningTransaction();
              })
          .isInstanceOf(TransactionAlreadyRunningException.class);
    }
  }

  @Nested
  class WhenAssertingInTransaction {
    @BeforeEach
    void setup() {}

    @Test
    void throwsIfRunning() {
      assertThatThrownBy(
              () -> {
                underTest.assertInTransaction();
              })
          .isInstanceOf(TransactionNotRunningException.class);
    }
  }
}

class Tx {}

class TestTransactionAwareProjection extends AbstractTransactionAwareProjection<Tx> {

  @Nullable
  @Override
  public FactStreamPosition factStreamPosition() {
    return null;
  }

  @Override
  public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {}

  @Override
  protected @NonNull Tx beginNewTransaction() {
    return new Tx();
  }

  @Override
  protected void rollback(@NonNull Tx runningTransaction) {}

  @Override
  protected void commit(@NonNull Tx runningTransaction) {}
}
