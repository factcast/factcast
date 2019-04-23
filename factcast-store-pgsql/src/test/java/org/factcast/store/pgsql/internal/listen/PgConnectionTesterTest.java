/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.store.pgsql.internal.listen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PgConnectionTesterTest {

    private PgConnectionTester uut;

    @Mock
    private PreparedStatement st;

    @Mock
    private ResultSet rs;

    @BeforeEach
    void setUp() {
        uut = new PgConnectionTester();
    }

    @Test
    void testTestPositive() throws Exception {
        Connection c = mock(Connection.class);
        when(c.prepareStatement(anyString())).thenReturn(st);
        when(st.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getInt(1)).thenReturn(42);
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

}
