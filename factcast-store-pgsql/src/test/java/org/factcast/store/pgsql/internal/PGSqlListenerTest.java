package org.factcast.store.pgsql.internal;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Predicate;

import org.factcast.store.pgsql.internal.PGListener.FactInsertionEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.eventbus.AsyncEventBus;
import com.impossibl.postgres.api.jdbc.PGConnection;
import com.impossibl.postgres.api.jdbc.PGNotificationListener;

@RunWith(MockitoJUnitRunner.class)
public class PGSqlListenerTest {

	@Mock
	PGConnectionSupplier ds;
	@Mock
	AsyncEventBus bus;
	@Mock
	PGConnection conn;
	@Mock
	PreparedStatement ps;
	@Mock
	Predicate<Connection> tester;
	@Captor
	ArgumentCaptor<PGNotificationListener> captor;

	@org.junit.Before
	public void setUp() throws SQLException {
		Mockito.when(ds.get()).thenReturn(conn);
		Mockito.when(conn.prepareStatement(anyString())).thenReturn(ps);
		Mockito.when(ps.execute()).thenReturn(true);

	}

	@Test
	public void testCheck() throws Exception {
		Mockito.when(ds.get()).thenReturn(conn);

		PGListener l = new PGListener(ds, bus, c -> true);
		l.afterPropertiesSet();
		l.check();
		verifyNoMoreInteractions(bus);
		verify(ds, times(1)).get();
	}

	@Test
	public void testCheckFails() throws Exception {
		Mockito.when(ds.get()).thenReturn(conn);
		Mockito.when(conn.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
		Mockito.when(tester.test(Mockito.any(PGConnection.class))).thenReturn(false, false, true);

		PGListener l = new PGListener(ds, bus, tester);
		l.afterPropertiesSet();
		l.check();
		l.check();
		l.check();
		verifyNoMoreInteractions(bus);
		verify(ds, times(3)).get();
	}

	@Test
	public void testListen() throws Exception {
		Mockito.when(ds.get()).thenReturn(conn);
		Mockito.when(conn.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));

		PGListener l = new PGListener(ds, bus, tester);
		l.afterPropertiesSet();
		verifyNoMoreInteractions(bus);
		verify(ds, times(1)).get();
		verify(conn, times(1)).addNotificationListener(Mockito.anyString(), Mockito.eq(PGConstants.CHANNEL_NAME),
				Mockito.any());

	}

	@Test
	public void testNotify() throws Exception {

		Mockito.doNothing().when(conn).addNotificationListener(Mockito.anyString(), eq(PGConstants.CHANNEL_NAME),
				captor.capture());

		PGListener l = new PGListener(ds, bus, tester);
		l.afterPropertiesSet();

		PGNotificationListener nl = captor.getValue();

		nl.notification(1, PGConstants.CHANNEL_NAME, "");
		nl.notification(1, PGConstants.CHANNEL_NAME, "");
		nl.notification(1, PGConstants.CHANNEL_NAME, "");

		verify(bus, times(3)).post(any(FactInsertionEvent.class));

	}

	@Test
	public void testStop() throws Exception {
		Mockito.when(ds.get()).thenReturn(conn);
		Mockito.when(conn.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
		PGListener l = new PGListener(ds, bus, tester);
		l.afterPropertiesSet();
		l.destroy();

		verify(conn).close();
	}

	@Test
	public void testStopWithoutStaring() throws Exception {
		Mockito.when(ds.get()).thenReturn(conn);
		Mockito.when(conn.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
		PGListener l = new PGListener(ds, bus, tester);
		l.destroy();

		verifyNoMoreInteractions(conn);
	}
}
