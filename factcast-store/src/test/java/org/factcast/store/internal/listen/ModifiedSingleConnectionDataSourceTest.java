/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.listen;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import lombok.SneakyThrows;
import org.factcast.store.internal.ConnectionModifier;
import org.factcast.store.internal.catchup.CatchupDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModifiedSingleConnectionDataSourceTest {

  @Mock private Connection c;
  @Mock private ConnectionModifier cm1;
  @Mock private ConnectionModifier cm2;

  @Test
  void throwsIllegalArgumentExceptionWhenConnectionIsNull() {
    assertThrows(IllegalArgumentException.class, () -> new CatchupDataSource(null, List.of(cm1)));
  }

  @Test
  void throwsNullPointerExceptionWhenModifiersIsNull() {
    assertThrows(NullPointerException.class, () -> new CatchupDataSource(c, null));
  }

  @Test
  void initializesWithEmptyModifiers() {
    var uut = new CatchupDataSource(c, List.of());
    assertThat(uut.modifiers()).isEmpty();
    uut.close();
  }

  @Test
  void callsAfterBorrowInOrder() {
    InOrder inOrder = inOrder(cm1, cm2);
    var uut = new CatchupDataSource(c, List.of(cm1, cm2));

    inOrder.verify(cm1).afterBorrow(c);
    inOrder.verify(cm2).afterBorrow(c);
    assertThat(uut.modifiers()).containsExactly(cm1, cm2);

    uut.close();
  }

  @Test
  void cleansUpConnection() {
    var uut = new CatchupDataSource(c, List.of(cm1));
    uut.close();

    verify(cm1).beforeReturn(c);
  }

  @Test
  void reversesOrderOnClose() {
    InOrder inOrder = inOrder(cm1, cm2);
    var uut = new CatchupDataSource(c, List.of(cm1, cm2));

    uut.close();

    inOrder.verify(cm2).beforeReturn(c);
    inOrder.verify(cm1).beforeReturn(c);
  }

  @Test
  @SneakyThrows
  void rollsBackOrphanedTransactionOnDestroy() {
    when(c.getAutoCommit()).thenReturn(false);
    var uut = new CatchupDataSource(c, List.of(cm1));
    uut.destroy();

    verify(c).rollback();
    verify(cm1).beforeReturn(c);
  }

  @Test
  @SneakyThrows
  void doesNotRollBackWhenAutoCommitOnDestroy() {
    when(c.getAutoCommit()).thenReturn(true);
    var uut = new CatchupDataSource(c, List.of(cm1));
    uut.destroy();

    verify(c, never()).rollback();
    verify(cm1).beforeReturn(c);
  }

  @Test
  @SneakyThrows
  void handlesSqlExceptionOnGetAutoCommit() {
    when(c.getAutoCommit()).thenThrow(new SQLException("fail"));
    var uut = new CatchupDataSource(c, List.of(cm1));
    uut.destroy();

    verify(c, never()).rollback();
    verify(cm1).beforeReturn(c);
  }

  @Test
  @SneakyThrows
  void handlesSqlExceptionOnDestroy() {
    when(c.getAutoCommit()).thenReturn(false);
    doThrow(new SQLException("fail")).when(c).rollback();
    var uut = new CatchupDataSource(c, List.of(cm1));
    uut.destroy();

    verify(c).rollback();
    verify(cm1).beforeReturn(c);
  }
}
