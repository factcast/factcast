/*
 * Copyright © 2017-2020 factcast.org
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
import static org.mockito.Mockito.*;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;

@ExtendWith(MockitoExtension.class)
public class PgConnectionTesterTest {

  private PgConnectionTester uut;

  @Mock private PreparedStatement st;

  @Mock private ResultSet rs;

  @BeforeEach
  void setUp() {
    uut = new PgConnectionTester();
  }

  @Test
  void testTestPositive() throws Exception {
    PgConnection c = mock(PgConnection.class);
    when(c.prepareStatement(anyString())).thenReturn(st);
    when(st.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(true, false);
    when(rs.getInt(1)).thenReturn(42);

    // needed for notitfy alive roundtrip which is currently disabled.
    // when(c.prepareCall(anyString())).thenReturn(mock(CallableStatement.class));
    // when(c.getNotifications(anyInt())).thenReturn(new PGNotification[] {
    // new Notification(
    // "alive", 1) });
    boolean test = uut.test(c);
    assertTrue(test);
  }

  @Test
  void testTestFailure() throws Exception {
    Connection c = mock(Connection.class);
    when(c.prepareStatement(anyString())).thenReturn(st);
    when(st.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(true, false);
    when(rs.getInt(1)).thenReturn(1);
    boolean test = uut.test(c);
    assertFalse(test);
  }

  @Test
  void testTestException1() throws Exception {
    Connection c = mock(Connection.class);
    when(c.prepareStatement(anyString())).thenReturn(st);
    when(st.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(true, false);
    when(rs.getInt(1)).thenThrow(new SQLException("BAM"));
    boolean test = uut.test(c);
    assertFalse(test);
  }

  @Test
  void testTestException2() throws Exception {
    Connection c = mock(Connection.class);
    when(c.prepareStatement(anyString())).thenReturn(st);
    when(st.executeQuery()).thenReturn(rs);
    when(rs.next()).thenThrow(new SQLException("BAM"));
    boolean test = uut.test(c);
    assertFalse(test);
  }

  @Test
  void testTestException3() throws Exception {
    Connection c = mock(Connection.class);
    when(c.prepareStatement(anyString())).thenReturn(st);
    when(st.executeQuery()).thenThrow(new SQLException("BAM"));
    boolean test = uut.test(c);
    assertFalse(test);
  }

  @Test
  void testTestException4() throws Exception {
    Connection c = mock(Connection.class);
    when(c.prepareStatement(anyString())).thenThrow(new SQLException("BAM"));
    boolean test = uut.test(c);
    assertFalse(test);
  }

  @Test
  public void testTestSelectStatement() throws Exception {
    Connection c = mock(PgConnection.class);
    PreparedStatement s = mock(PreparedStatement.class);
    when(s.executeQuery()).thenThrow(new SQLException("BAM"));
    when(c.prepareStatement(anyString())).thenReturn(s);
    boolean test = uut.testSelectStatement(c);
    assertFalse(test);
  }

  @Test
  public void testTestNotificationRoundTripThrowsException() throws Exception {
    Connection c = mock(PgConnection.class);
    CallableStatement s = mock(CallableStatement.class);
    when(s.execute()).thenThrow(new SQLException("BAM"));
    when(c.prepareCall(anyString())).thenReturn(s);
    boolean test = uut.testNotificationRoundTrip(c);
    assertFalse(test);
  }

  @Test
  public void testTestNotificationRoundTripReturnsNull() throws Exception {
    PgConnection c = mock(PgConnection.class);
    CallableStatement s = mock(CallableStatement.class);
    when(c.prepareCall(anyString())).thenReturn(s);
    when(c.getNotifications(anyInt())).thenReturn(null);
    boolean test = uut.testNotificationRoundTrip(c);
    assertFalse(test);
  }

  @Test
  public void testTestNotificationRoundTripReturnsEmptyArray() throws Exception {
    PgConnection c = mock(PgConnection.class);
    CallableStatement s = mock(CallableStatement.class);
    when(c.prepareCall(anyString())).thenReturn(s);
    when(c.getNotifications(anyInt())).thenReturn(new PGNotification[0]);
    boolean test = uut.testNotificationRoundTrip(c);
    assertFalse(test);
  }

  @Test
  public void testTestNotificationRoundTripReturnsAsExpected() throws Exception {
    PgConnection c = mock(PgConnection.class);
    CallableStatement s = mock(CallableStatement.class);
    when(c.prepareCall(anyString())).thenReturn(s);
    when(c.getNotifications(anyInt()))
        .thenReturn(new PGNotification[] {mock(PGNotification.class)});
    boolean test = uut.testNotificationRoundTrip(c);
    assertTrue(test);
  }
}
