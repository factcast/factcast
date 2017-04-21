package org.factcast.server.grpc.api.conv;

import static org.junit.Assert.*;

import java.util.UUID;

import org.factcast.core.DefaultFactFactory;
import org.factcast.core.store.subscription.FactSpec;
import org.factcast.server.grpc.api.conv.ProtoConverter;
import org.factcast.server.grpc.api.conv.StringConverter;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_FactSpec;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_OptionalInt64;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_OptionalString;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_SubscriptionRequest;
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

		assertEquals(null, uut.fromProto(uut.toProto(s)).type());
		assertEquals(null, uut.fromProto(uut.toProto(s)).aggId());
		assertEquals(null, uut.fromProto(uut.toProto(s)).jsFilterScript());

	}

	


}
