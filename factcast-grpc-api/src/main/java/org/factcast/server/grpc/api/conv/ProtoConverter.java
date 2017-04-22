package org.factcast.server.grpc.api.conv;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.subscription.SubscriptionRequest;
import org.factcast.core.store.subscription.SubscriptionRequestTO;
import org.factcast.core.util.FCJson;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Fact;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Fact.Builder;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification.Type;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_UUID;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessageV3;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

//TODO add symetry tests
@RequiredArgsConstructor
public class ProtoConverter {

	public MSG_Notification toIdNotification(UUID t) {
		org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification.Builder b = MSG_Notification.newBuilder()
				.setType(MSG_Notification.Type.Id);
		return b.setId(toProto(t)).build();
	}

	public MSG_Notification toCatchupNotification() {
		return MSG_Notification.newBuilder().setType(MSG_Notification.Type.Catchup).build();
	}

	public MSG_Notification toCompleteNotification() {
		return MSG_Notification.newBuilder().setType(MSG_Notification.Type.Complete).build();

	}

	public MSG_Notification toNotification(Fact t) {
		org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification.Builder b = MSG_Notification.newBuilder()
				.setType(MSG_Notification.Type.Fact);
		b.setFact(toProto(t));
		b.setType(Type.Fact);
		return b.build();

	}

	public MSG_Notification toIdNotification(Fact t) {
		org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification.Builder b = MSG_Notification.newBuilder()
				.setType(MSG_Notification.Type.Id);
		b.setId(toProto(t.id()));
		b.setType(Type.Id);
		return b.build();
	}

	public MSG_UUID toProto(@NonNull UUID t) {
		return MSG_UUID.newBuilder().setLsb(t.getLeastSignificantBits()).setMsb(t.getMostSignificantBits()).build();
	}

	@SneakyThrows
	public SubscriptionRequest fromProto(@NonNull MSG_SubscriptionRequest request) {
		return FCJson.reader().forType(SubscriptionRequestTO.class).readValue(request.getJson());
	}

	@SneakyThrows
	public MSG_SubscriptionRequest toProto(SubscriptionRequest request) {

		SubscriptionRequestTO storeSubscriptionRequest = new SubscriptionRequestTO(request);

		return MSG_SubscriptionRequest.newBuilder()
				.setJson(FCJson.writer().writeValueAsString(storeSubscriptionRequest)).build();
	}

	public UUID fromProto(MSG_UUID request) {
		long lsb = request.getLsb();
		long msb = request.getMsb();

		return new UUID(msb, lsb);
	}

	public Fact fromProto(MSG_Fact protoFact) {
		return Fact.of(protoFact.getHeader(), protoFact.getPayload());
	}

	public MSG_Fact toProto(org.factcast.core.Fact factMark) {
		Builder proto = MSG_Fact.newBuilder();
		proto.setPresent(true);
		proto.setHeader(factMark.jsonHeader());
		proto.setPayload(factMark.jsonPayload());
		return proto.build();
	}

	public MSG_Fact toProto(Optional<Fact> optionalFact) {
		Builder proto = MSG_Fact.newBuilder();
		boolean present = optionalFact.isPresent();
		proto.setPresent(present);
		if (present) {
			Fact fact = optionalFact.get();
			proto.setHeader(fact.jsonHeader());
			proto.setPayload(fact.jsonPayload());
		}
		return proto.build();
	}

	public UUID fromIdNotification(@NonNull MSG_Notification n) {
		if (n.getType() != MSG_Notification.Type.Id) {
			throw new IllegalArgumentException("Wrong notification type");
		}

		return fromProto(n.getId());
	}

	public static FieldDescriptor getRequiredFieldDescriptor(GeneratedMessageV3 msg, String fieldName) {

		FieldDescriptor fd = msg.getDescriptorForType().findFieldByName(fieldName);
		if (fd == null) {
			throw new NoSuchFieldError(
					"Unknown field '" + fieldName + "' for Type '" + msg.getDescriptorForType().getFullName() + "'");
		}
		return fd;
	}

}
