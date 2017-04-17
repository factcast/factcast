package org.factcast.store.pgsql.internal;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
//FIXME can be a junit test
@RunWith(MockitoJUnitRunner.class)
public class ConnectionTesterTest {

	@Mock
	Connection conn;

	@Mock
	PreparedStatement ps;
	@Mock 
	ResultSet rs;
	
	@Test
	public void testTest() throws Exception {
		ConnectionTester tester = new ConnectionTester();

		when(conn.prepareStatement(anyString())).thenReturn(ps);
		when(ps.executeQuery()).thenReturn(rs);
		when(rs.getInt(1)).thenReturn(42);
		
		assertTrue(tester.test(conn));
		
		verify(rs).next();
		verify(rs).getInt(1);
	}
}
