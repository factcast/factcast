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
  private TestTransactionAwareProjection underTest = new TestTransactionAwareProjection();

  @Nested
  class WhenBegining {
    @BeforeEach
    void setup() {}

    @Test
    void test() {
      Assertions.assertThat(underTest.inTransaction()).isFalse();
      underTest.begin();
      Assertions.assertThat(underTest.inTransaction()).isTrue();
    }
  }

  @Nested
  class WhenCommitting {
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenRollbacking {
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenAssertingNoRunningTransaction {
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenAssertingInTransaction {
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenTestingInTransaction {
    @BeforeEach
    void setup() {}
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
