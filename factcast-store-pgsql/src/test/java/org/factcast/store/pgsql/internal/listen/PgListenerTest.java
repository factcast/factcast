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
package org.factcast.store.pgsql.internal.listen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.factcast.store.pgsql.internal.PgConstants;
import org.factcast.store.pgsql.internal.listen.PgListener.FactInsertionEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.PGNotification;
import org.postgresql.core.Notification;
import org.postgresql.jdbc.PgConnection;

import com.google.common.eventbus.AsyncEventBus;

import lombok.val;

@ExtendWith(MockitoExtension.class)
public class PgListenerTest {

    @Mock
    PgConnectionSupplier ds;

    @Mock
    AsyncEventBus bus;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    PgConnection conn;

    @Mock
    PreparedStatement ps;

    PGNotification[] someNotification = new PGNotification[] { //
            new Notification(PgConstants.CHANNEL_NAME, 1) };

    @Captor
    ArgumentCaptor<FactInsertionEvent> factCaptor;

    @Test
    void testRegisterPostgresListeners() throws Exception {
        when(ds.get()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(conn.getNotifications(anyInt())).thenReturn(null);
        when(conn.prepareCall(anyString()).execute()).thenReturn(true);

        PgListener l = new PgListener(ds, bus);
        l.afterPropertiesSet();
        sleep(100);
        l.destroy();

        verify(bus).post(any(FactInsertionEvent.class));
        verifyNoMoreInteractions(bus);
        verify(conn, atLeastOnce()).prepareStatement(PgConstants.LISTEN_SQL);
        verify(conn, atLeastOnce()).prepareStatement(PgConstants.LISTEN_ROUNDTRIP_CHANNEL_SQL);
        verify(ps, atLeastOnce()).execute();
    }

    @Test
    void testNotify() throws Exception {
        when(ds.get()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(conn.prepareCall(anyString()).execute()).thenReturn(true);
        when(conn.getNotifications(anyInt())).thenReturn(new PGNotification[] { //
                new Notification(PgConstants.CHANNEL_NAME, 1), //
                new Notification(PgConstants.CHANNEL_NAME, 1), //
                new Notification(PgConstants.CHANNEL_NAME, 1) },
                new PGNotification[] { new Notification(PgConstants.CHANNEL_NAME, 2) }, //
                new PGNotification[] { new Notification(PgConstants.CHANNEL_NAME, 3) }, null, null,
                null);

        PgListener l = new PgListener(ds, bus);
        l.afterPropertiesSet();
        sleep(500);
        l.destroy();

        verify(bus, atLeastOnce()).post(factCaptor.capture());
        val allEvents = factCaptor.getAllValues();

        // first event is the general wakeup to the subscribers after startup
        assertEquals("scheduled-poll", allEvents.get(0).name());
        // events 2 - incl. 4 are notifies
        assertTrue(allEvents.subList(1, 4)
                .stream()
                .allMatch(event -> event.name().equals(PgConstants.CHANNEL_NAME)));

        // in total there are only 3 notifies
        long totalNotifyCount = allEvents.stream()
                .filter(f -> f.name().equals(PgConstants.CHANNEL_NAME))
                .count();
        assertEquals(3, totalNotifyCount);
    }

    @Test
    void testNotifyAfterConnectionResetScheduled() throws Exception {
        when(ds.get()).thenReturn(conn);
        when(conn.prepareCall(anyString()).execute()).thenReturn(true);
        when(conn.getNotifications(anyInt())).thenReturn(null);

        PgListener l = new PgListener(ds, bus);
        l.afterPropertiesSet();
        sleep(200);
        l.destroy();

        // one scheduled
        verify(bus, atLeastOnce()).post(factCaptor.capture());
        // all captured events are schedule events
        assertTrue(factCaptor.getAllValues()
                .stream()
                .allMatch(event -> event.name().equals("scheduled-poll")));
    }

    @Test
    void noUnrelatedNotifiesArePosted() throws Exception {
        val unrelatedNotifyName = "I don't belong here";

        when(ds.get()).thenReturn(conn);
        when(conn.prepareCall(anyString()).execute()).thenReturn(true);
        when(conn.getNotifications(anyInt())).thenReturn(new PGNotification[] {
                new Notification(unrelatedNotifyName, 1) }, null, null);

        PgListener l = new PgListener(ds, bus);
        l.afterPropertiesSet();
        sleep(100);
        l.destroy();

        // capture output
        verify(bus, atLeastOnce()).post(factCaptor.capture());
        assertTrue(factCaptor.getAllValues()
                .stream()
                .noneMatch(event -> event.name().equals(unrelatedNotifyName)));
    }

    @Test
    void testStop() throws Exception {
        when(ds.get()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));

        PgListener l = new PgListener(ds, bus);
        l.afterPropertiesSet();
        l.destroy();
        sleep(150);// TODO flaky
        verify(conn).close();
    }

    @Test
    void testStopWithoutStarting() {
        PgListener l = new PgListener(ds, bus);
        l.destroy();
        verifyNoMoreInteractions(conn);
    }

    @Test
    public void successfulProbesAreForwarded() throws SQLException {
        when(conn.getNotifications(anyInt())).thenReturn(someNotification);

        PgListener l = new PgListener(ds, bus);
        val result = l.sendProbeAndWaitForEcho(conn);
        assertEquals(1, result.length);
        assertEquals(PgConstants.CHANNEL_NAME, result[0].getName());
    }

    @Test
    public void sqlExceptionAfterUnsuccessfulProbe() throws SQLException {
        when(conn.getNotifications(anyInt())).thenReturn(null);
        Assertions.assertThrows(SQLException.class, () -> {
            PgListener l = new PgListener(ds, bus);
            l.sendProbeAndWaitForEcho(conn);
        });
    }

    private void sleep(int i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException ignored) {
        }
    }
}
