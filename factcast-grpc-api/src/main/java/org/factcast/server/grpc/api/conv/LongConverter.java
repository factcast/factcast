package org.factcast.server.grpc.api.conv;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

import org.factcast.server.grpc.gen.FactStoreProto.MSG_OptionalInt64;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessageV3;

import lombok.NonNull;

public class LongConverter {
	public static void setOptionalLong(Long l, Consumer<MSG_OptionalInt64> setter) {
		// holy crap!
		Optional.ofNullable(l).map(LongConverter::toProto).ifPresent(setter::accept);
	}

	public static MSG_OptionalInt64 toProto(@NonNull Long l) {
		org.factcast.server.grpc.gen.FactStoreProto.MSG_OptionalInt64.Builder b = MSG_OptionalInt64.newBuilder();
		if (l != null) {
			b.setValue(l);
		}
		return b.build();
	}
	

	public static OptionalLong fromProto(GeneratedMessageV3 msg, String fieldName) {
		FieldDescriptor fdesc = ProtoConverter.getRequiredFieldDescriptor(msg, fieldName);
		if (msg.hasField(fdesc)) {
			return OptionalLong.of(((MSG_OptionalInt64) msg.getField(fdesc)).getValue());
		} else {
			return OptionalLong.empty();
		}
	}
}
