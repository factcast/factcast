package org.factcast.server.grpc.service;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Facts;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.grpc.stub.StreamObserver;

@RunWith(MockitoJUnitRunner.class)
public class FactStoreGrpcServiceTest {
	@Mock
	FactStore backend;
	FactStoreGrpcService uut;

	@Captor
	ArgumentCaptor<List<Fact>> c;

	@Test
	public void testpublish() throws Exception {
		uut = new FactStoreGrpcService(backend);
		doNothing().when(backend).publish(c.capture());
		MSG_Facts r = MSG_Facts.newBuilder().build();

		uut.publish(r, mock(StreamObserver.class));

		verify(backend).publish(anyList());

		assertTrue(c.getValue().isEmpty());
	}

}
