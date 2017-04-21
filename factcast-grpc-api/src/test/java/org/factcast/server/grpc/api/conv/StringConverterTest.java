package org.factcast.server.grpc.api.conv;

import static org.junit.Assert.*;

import java.util.Optional;

import org.factcast.server.grpc.gen.FactStoreProto.MSG_FactSpec;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_OptionalString;
import org.junit.Test;

public class StringConverterTest {

	@Test
	public void testFromProtoUnset() throws Exception {
		MSG_FactSpec spec = MSG_FactSpec.newBuilder().build();
		assertFalse(StringConverter.fromProto(spec, "jsFilter").isPresent());
	}

	@Test
	public void testFromProtoSet() throws Exception {
		MSG_FactSpec spec = MSG_FactSpec.newBuilder().setJsFilter(MSG_OptionalString.newBuilder().setValue("foo")).build();
		Optional<String> jsFilter = StringConverter.fromProto(spec, "jsFilter");
		assertTrue(jsFilter.isPresent());
		assertEquals("foo",jsFilter.get());
	}

	@Test(expected = NoSuchFieldError.class)
	public void testFromOptionalString_mustFailForUnknownField() throws Exception {
		MSG_FactSpec msg = MSG_FactSpec.newBuilder().build();
		StringConverter.fromProto(msg, "unknownField");
	}
}
