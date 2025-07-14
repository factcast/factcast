/*
 * Copyright © 2017-2025 factcast.org
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.sql.*;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectionModifierTest {
  @Mock Connection c;

  @Nested
  class DisableAutoCommit {

    @SneakyThrows
    @Test
    void togglesAutoCommit() {
      var uut = new ConnectionModifier.DisableAutoCommit();
      uut.afterBorrow(c);
      Mockito.verify(c).setAutoCommit(false);

      Mockito.reset(c);
      uut.beforeReturn(c);
      Mockito.verify(c).setAutoCommit(true);
    }
  }

  @Nested
  class DisableBitmapScan {

    @Mock PreparedStatement p;

    @SneakyThrows
    @Test
    void togglesBitmapScan() {
      var uut = new ConnectionModifier.DisableBitmapScan();
      when(c.createStatement()).thenReturn(p);
      uut.afterBorrow(c);
      Mockito.verify(p).execute("SET enable_bitmapscan='off'");

      Mockito.reset(p);
      uut.beforeReturn(c);
      Mockito.verify(p).execute("RESET enable_bitmapscan");
    }
  }

  @Nested
  class Property {

    @Mock PreparedStatement p;
    @Mock ResultSet r;
    @Mock ResultSetMetaData rsmd;

    @SneakyThrows
    @Test
    void togglesProperty() {
      var uut = new ConnectionModifier.Property("myproperty", "new");
      when(c.createStatement()).thenReturn(p);
      when(r.next()).thenReturn(true, false);
      when(r.getMetaData()).thenReturn(rsmd);
      when(rsmd.getColumnCount()).thenReturn(1);
      when(r.getString(1)).thenReturn("old");
      when(p.executeQuery("SHOW myproperty")).thenReturn(r);
      uut.afterBorrow(c);
      Mockito.verify(p).execute("SET myproperty='new'");

      Mockito.reset(p);
      uut.beforeReturn(c);
      Mockito.verify(p).execute("SET myproperty='old'");
    }
  }

  @Nested
  class Withers {
    @Test
    void checkPropertyWither() {
      Assertions.assertThat(ConnectionModifier.withApplicationName("gurke"))
          .isInstanceOf(ConnectionModifier.Property.class)
          .isEqualTo(new ConnectionModifier.Property("application_name", "gurke"));
    }

    @Test
    void checkAutoCommitWither() {
      Assertions.assertThat(ConnectionModifier.withAutoCommitDisabled())
          .isInstanceOf(ConnectionModifier.DisableAutoCommit.class);
    }

    @Test
    void checkBitmapScanWither() {
      Assertions.assertThat(ConnectionModifier.withBitmapScanDisabled())
          .isInstanceOf(ConnectionModifier.DisableBitmapScan.class);
    }
  }
}
