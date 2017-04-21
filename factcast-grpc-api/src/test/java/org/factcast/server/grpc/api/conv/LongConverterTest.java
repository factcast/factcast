package org.factcast.server.grpc.api.conv;

import static org.junit.Assert.*;

import java.util.Optional;
import java.util.OptionalLong;

import org.factcast.server.grpc.gen.FactStoreProto.MSG_FactSpec;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_OptionalInt64;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_SubscriptionRequest.Builder;
import org.junit.Test;

public class LongConverterTest {

	@Test
	public void testFromProtoUnset() throws Exception {
		MSG_SubscriptionRequest r = MSG_SubscriptionRequest.newBuilder().build();
		OptionalLong max = LongConverter.fromProto(r, "maxLatency");
		assertFalse(max.isPresent());
	}

	@Test
	public void testFromProtoSet() throws Exception {
		MSG_SubscriptionRequest r = MSG_SubscriptionRequest.newBuilder().setMaxLatency(MSG_OptionalInt64.newBuilder().setValue(7)).build();
		OptionalLong max = LongConverter.fromProto(r, "maxLatency");
		assertTrue(max.isPresent());
		assertEquals(7,max.getAsLong());
	}

	@Test(expected = NoSuchFieldError.class)
	public void testFromOptionalLong_mustFailForUnknownField() throws Exception {
		MSG_FactSpec msg = MSG_FactSpec.newBuilder().build();
		LongConverter.fromProto(msg, "unknownField");
	}
}
