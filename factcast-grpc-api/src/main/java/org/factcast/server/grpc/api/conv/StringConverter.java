package org.factcast.server.grpc.api.conv;

import java.util.Optional;
import java.util.function.Consumer;

import org.factcast.server.grpc.gen.FactStoreProto;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_OptionalString;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_OptionalString.Builder;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessageV3;

import lombok.NonNull;

public class StringConverter {

	public static void setOptionalString(String type, Consumer<MSG_OptionalString> setter) {
		// holy crap!
		Optional.ofNullable(type).map(StringConverter::toProto).ifPresent(setter::accept);
	}

	public static MSG_OptionalString toProto(@NonNull String s) {
		org.factcast.server.grpc.gen.FactStoreProto.MSG_OptionalString.Builder b = MSG_OptionalString.newBuilder();
		if (s != null) {
			b.setValue(s);
		}
		return b.build();
	}
	

	public static Optional<String> fromProto(GeneratedMessageV3 msg, String fieldName) {
		FieldDescriptor fdesc = ProtoConverter.getRequiredFieldDescriptor(msg, fieldName);
		if (msg.hasField(fdesc)) {
			return Optional.ofNullable(((MSG_OptionalString) msg.getField(fdesc)).getValue());
		} else {
			return Optional.empty();
		}
	}

}
