package org.factcast.server.grpc.service;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.store.FactStore;
import org.factcast.server.grpc.api.conv.ProtoConverter;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Fact;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Facts;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Facts.Builder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.grpc.stub.StreamObserver;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("unchecked")
public class FactStoreGrpcServiceTest {
	@Mock
	FactStore backend;
	FactStoreGrpcService uut;

	@Captor
	ArgumentCaptor<List<Fact>> acFactList;
	@Captor
	ArgumentCaptor<UUID> acUUID;

	ProtoConverter protoConverter = new ProtoConverter();

	@Test(expected = NullPointerException.class)
	public void testPublishNull() throws Exception {
		uut = new FactStoreGrpcService(backend);

		uut.publish(null, mock(StreamObserver.class));
	}

	@Test
	public void testPublishNone() throws Exception {
		uut = new FactStoreGrpcService(backend);
		doNothing().when(backend).publish(acFactList.capture());
		MSG_Facts r = MSG_Facts.newBuilder().build();

		uut.publish(r, mock(StreamObserver.class));

		verify(backend).publish(anyList());

		assertTrue(acFactList.getValue().isEmpty());
	}

	@Test
	public void testPublishSome() throws Exception {
		uut = new FactStoreGrpcService(backend);
		doNothing().when(backend).publish(acFactList.capture());
		Builder b = MSG_Facts.newBuilder();

		TestFact f1 = new TestFact();
		TestFact f2 = new TestFact();
		MSG_Fact msg1 = protoConverter.toProto(f1);
		MSG_Fact msg2 = protoConverter.toProto(f2);

		b.addAllFact(Arrays.asList(msg1, msg2));
		MSG_Facts r = b.build();

		uut.publish(r, mock(StreamObserver.class));

		verify(backend).publish(anyList());

		List<Fact> facts = acFactList.getValue();
		assertFalse(facts.isEmpty());
		assertEquals(2, facts.size());
		assertEquals(f1.id(), facts.get(0).id());
		assertEquals(f2.id(), facts.get(1).id());

	}

	@Test(expected = NullPointerException.class)
	public void testFetchByIdNull() throws Exception {
		uut.fetchById(null, mock(StreamObserver.class));
	}

	@Test(expected = NullPointerException.class)
	public void testFetchById() throws Exception {
		UUID id = UUID.randomUUID();
		uut.fetchById(protoConverter.toProto(id), mock(StreamObserver.class));

		verify(backend).fetchById(eq(id));

	}
}
