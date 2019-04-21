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
package org.factcast.store.pgsql.internal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.function.*;

import org.factcast.store.pgsql.internal.listen.*;
import org.factcast.store.pgsql.internal.listen.PgListener.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.postgresql.*;
import org.postgresql.core.*;
import org.postgresql.jdbc.*;

import com.google.common.eventbus.*;

@ExtendWith(MockitoExtension.class)
public class PgSqlListenerTest {

    @Mock
    PgConnectionSupplier ds;

    @Mock
    AsyncEventBus bus;

    @Mock
    PgConnection conn;

    @Mock
    PreparedStatement ps;

    Predicate<Connection> tester = c -> true;

    @Captor
    ArgumentCaptor<PgListener> captor;

    @SuppressWarnings("unchecked")
    @Test
    void testCheckFails() throws SQLException {

        Mockito.when(ds.get()).thenReturn(conn);
        Mockito.when(conn.prepareStatement(anyString())).thenReturn(ps);

        tester = mock(Predicate.class);
        Mockito.when(tester.test(any())).thenReturn(false, false, true, false);
        PgListener l = new PgListener(ds, bus, tester);
        l.afterPropertiesSet();
        verify(bus, times(3)).post(any(FactInsertionEvent.class));
        verifyNoMoreInteractions(bus);

        l.destroy();
    }

    @Test
    void testListen() throws Exception {
        Mockito.when(ds.get()).thenReturn(conn);
        Mockito.when(conn.prepareStatement(anyString())).thenReturn(ps);

        PgListener l = new PgListener(ds, bus, tester);
        l.afterPropertiesSet();
        sleep(100);
        verify(bus).post(any(FactInsertionEvent.class));
        verifyNoMoreInteractions(bus);
        verify(conn).prepareStatement(PgConstants.LISTEN_SQL);
        verify(ps).execute();

        l.destroy();
    }

    @Test
    void testNotify() throws Exception {

        Mockito.when(ds.get()).thenReturn(conn);
        Mockito.when(conn.prepareStatement(anyString())).thenReturn(ps);

        PgListener l = new PgListener(ds, bus, tester);
        when(conn.getNotifications(anyInt())).thenReturn(new PGNotification[]{ //
                        new Notification(PgConstants.CHANNEL_NAME, 1), //
                        new Notification(PgConstants.CHANNEL_NAME, 1), //
                        new Notification(PgConstants.CHANNEL_NAME, 1)}, new PGNotification[]{
                        new Notification(PgConstants.CHANNEL_NAME, 2)}, //
                new PGNotification[]{new Notification(PgConstants.CHANNEL_NAME, 3)}, null);
        l.afterPropertiesSet();
        sleep(400);
        // 4 posts: one scheduled, 3 from notifications
        verify(bus, times(4)).post(any(FactInsertionEvent.class));

        l.destroy();
    }

    @Test
    void testNotifyScheduled() throws Exception {

        Mockito.when(ds.get()).thenReturn(conn);
        Mockito.when(conn.prepareStatement(anyString())).thenReturn(ps);

        PgListener l = new PgListener(ds, bus, tester);
        when(conn.getNotifications(anyInt())).thenReturn(null);
        l.afterPropertiesSet();
        sleep(200);
        // one scheduled
        verify(bus, times(1)).post(any(FactInsertionEvent.class));

        l.destroy();
    }

    @Test
    void testStop() throws Exception {
        Mockito.when(ds.get()).thenReturn(conn);
        Mockito.when(conn.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
        PgListener l = new PgListener(ds, bus, tester);
        l.afterPropertiesSet();
        l.destroy();
        sleep(150);//TODO flaky
        verify(conn).close();
    }

    private void sleep(int i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException ignored) {
        }
    }

    @Test
    void testStopWithoutStarting() {
        PgListener l = new PgListener(ds, bus, tester);
        l.destroy();
        verifyNoMoreInteractions(conn);
    }
}
