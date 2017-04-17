package org.factcast.server.grpc.api;

import static org.junit.Assert.*;

import java.util.UUID;

import org.factcast.core.DefaultFactFactory;
import org.factcast.core.store.subscription.FactSpec;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ProtoConverterTest {
	ProtoConverter uut = new ProtoConverter(new DefaultFactFactory(new ObjectMapper()));

	@Test
	public void testIdNotification() throws Exception {
		UUID uuid = UUID.randomUUID();
		assertEquals(uuid, uut.fromIdNotification(uut.toIdNotification(uuid)));

	}

	@Test
	public void testFacSpec() throws Exception {
		FactSpec s = FactSpec.ns("default");

		assertEquals(s, uut.fromProto(uut.toProto(s)));

	}
}
