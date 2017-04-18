package org.factcast.server.grpc.api;

import static org.junit.Assert.*;

import java.util.UUID;

import org.factcast.core.DefaultFactFactory;
import org.factcast.core.store.subscription.FactSpec;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_FactSpec;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.server.grpc.gen.FactStoreProto.OptionalInt64;
import org.factcast.server.grpc.gen.FactStoreProto.OptionalString;
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

	@Test(expected = NoSuchFieldError.class)
	public void testFromOptionalString_mustFailForUnknownField() throws Exception {
		MSG_FactSpec msg = MSG_FactSpec.newBuilder().build();
		uut.fromOptionalString(msg, "unknownField");
	}

	@Test
	public void testFromOptionalString_empty() throws Exception {
		MSG_FactSpec msg = MSG_FactSpec.newBuilder().build();
		assertFalse(uut.fromOptionalString(msg, "type").isPresent());
	}

	@Test
	public void testFromOptionalString_set() throws Exception {
		MSG_FactSpec msg = MSG_FactSpec.newBuilder().setType(OptionalString.newBuilder().setValue("yo").build())
				.build();
		assertEquals("yo", uut.fromOptionalString(msg, "type").get());
	}

	@Test(expected = NoSuchFieldError.class)
	public void testFromOptionalInt64String_mustFailForUnknownField() throws Exception {

		MSG_SubscriptionRequest msg = MSG_SubscriptionRequest.newBuilder().build();
		uut.fromOptionalInt64(msg, "unknown");
	}

	@Test
	public void testFromOptionalInt64_empty() throws Exception {
		MSG_SubscriptionRequest msg = MSG_SubscriptionRequest.newBuilder().build();
		assertFalse(uut.fromOptionalInt64(msg, "maxLatency").isPresent());
	}

	@Test
	public void testFromOptionalInt64_set() throws Exception {
		MSG_SubscriptionRequest msg = MSG_SubscriptionRequest.newBuilder()
				.setMaxLatency(OptionalInt64.newBuilder().setValue(123).build()).build();
		assertEquals(123, uut.fromOptionalInt64(msg, "maxLatency").getAsLong());
	}

}
