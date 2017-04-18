package org.factcast.server.grpc.api;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.factcast.core.DefaultFactFactory;
import org.factcast.core.Fact;
import org.factcast.core.store.subscription.FactSpec;
import org.factcast.core.store.subscription.SubscriptionRequest;
import org.factcast.core.store.subscription.SubscriptionRequest.SpecBuilder;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Fact;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Fact.Builder;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_FactSpec;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification.Type;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_UUID;
import org.factcast.server.grpc.gen.FactStoreProto.OptionalInt64;
import org.factcast.server.grpc.gen.FactStoreProto.OptionalString;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessageV3;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

//TODO add symetry tests
@RequiredArgsConstructor
public class ProtoConverter {

	private final DefaultFactFactory factory;
	private static final MSG_Fact NO_FACT = MSG_Fact.newBuilder().setPresent(false).build();

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

	public MSG_FactSpec toProto(FactSpec spec) {
		org.factcast.server.grpc.gen.FactStoreProto.MSG_FactSpec.Builder b = MSG_FactSpec.newBuilder();

		setOptionalString(spec.type(), b::setType);
		setOptionalString(spec.jsFilterScript(), b::setJsFilter);
		setOptionalString(spec.ns(), b::setNs);

		Optional.ofNullable(spec.aggId()).map(this::toProto).ifPresent(b::setAggId);

		return b.build();
	}

	private void setOptionalString(String type, Consumer<OptionalString> setter) {
		// holy crap!
		Optional.ofNullable(type).map(this::toOptionalString).ifPresent(setter::accept);
	}

	private OptionalString toOptionalString(@NonNull String s) {
		org.factcast.server.grpc.gen.FactStoreProto.OptionalString.Builder b = OptionalString.newBuilder();
		if (s != null) {
			b.setValue(s);
		}
		return b.build();
	}

	public FactSpec fromProto(@NonNull MSG_FactSpec spec) {

		FactSpec s = FactSpec.ns(fromOptionalString(spec, "ns").orElse("default"));
		fromOptionalString(spec, "type").ifPresent(s::type);
		fromOptionalString(spec, "jsFilter").ifPresent(s::jsFilterScript);

		if (spec.hasAggId()) {
			s.aggId(fromProto(spec.getAggId()));
		}
		// TODO add meta
		return s;
	}

	public SubscriptionRequest fromProto(@NonNull MSG_SubscriptionRequest request) {

		List<FactSpec> specs = request.getSpecsList().stream().map(this::fromProto).collect(Collectors.toList());
		FactSpec firstSpec = specs.remove(0);
		boolean follow = request.getContinous();

		OptionalLong optionalMaxLatency = fromOptionalInt64(request, "maxLatency");

		SpecBuilder r;
		if (follow) {
			if (optionalMaxLatency.isPresent()) {
				r = SubscriptionRequest.follow(optionalMaxLatency.getAsLong(), firstSpec);
			} else {
				r = SubscriptionRequest.follow(firstSpec);
			}
		} else {
			r = SubscriptionRequest.catchup(firstSpec);
		}

		specs.forEach(s -> r.or(s));

		if (request.hasSince()) {
			return r.since(fromProto(request.getSince()));
		} else {
			return r.sinceInception();
		}
	}

	public MSG_SubscriptionRequest toProto(SubscriptionRequest request, boolean idOnly) {
		long maxLatency = Math.max(request.maxLatencyInMillis(), 10);
		boolean follow = request.continous();
		List<MSG_FactSpec> specs = request.specs().stream().map(this::toProto).collect(Collectors.toList());

		org.factcast.server.grpc.gen.FactStoreProto.MSG_SubscriptionRequest.Builder b = MSG_SubscriptionRequest
				.newBuilder();
		b.setIdOnly(idOnly);
		b.setContinous(follow);
		b.setMaxLatency(OptionalInt64.newBuilder().setValue(maxLatency).build());
		Optional<UUID> since = request.startingAfter();
		if (since.isPresent()) {
			b.setSince(toProto(since.get()));
		}
		b.addAllSpecs(specs);

		return b.build();
	}

	public MSG_Fact toProto(Optional<Fact> fetchById) {
		return fetchById.map(this::toProto).orElse(MSG_Fact.newBuilder(NO_FACT).build());
	}

	public UUID fromProto(MSG_UUID request) {
		long lsb = request.getLsb();
		long msb = request.getMsb();

		return new UUID(msb, lsb);
	}

	public Fact fromProto(MSG_Fact protoFact) {
		return factory.create(protoFact.getHeader(), protoFact.getPayload());
	}

	public MSG_Fact toProto(org.factcast.core.Fact factMark) {
		Builder proto = MSG_Fact.newBuilder();
		proto.setPresent(true);
		proto.setHeader(factMark.jsonHeader());
		proto.setPayload(factMark.jsonPayload());
		return proto.build();
	}

	public UUID fromIdNotification(@NonNull MSG_Notification n) {
		if (n.getType() != MSG_Notification.Type.Id) {
			throw new IllegalArgumentException("Wrong notification type");
		}

		return fromProto(n.getId());
	}

	public Optional<String> fromOptionalString(GeneratedMessageV3 msg, String fieldName) {
		FieldDescriptor fdesc = getRequiredFieldDescriptor(msg, fieldName);
		if (msg.hasField(fdesc)) {
			return Optional.ofNullable(((OptionalString) msg.getField(fdesc)).getValue());
		} else {
			return Optional.empty();
		}
	}

	public OptionalLong fromOptionalInt64(GeneratedMessageV3 msg, String fieldName) {
		FieldDescriptor fdesc = getRequiredFieldDescriptor(msg, fieldName);
		if (msg.hasField(fdesc)) {
			return OptionalLong
					.of(((org.factcast.server.grpc.gen.FactStoreProto.OptionalInt64) msg.getField(fdesc)).getValue());
		} else {
			return OptionalLong.empty();
		}

	}

	private static FieldDescriptor getRequiredFieldDescriptor(GeneratedMessageV3 msg, String fieldName) {

		FieldDescriptor fd = msg.getDescriptorForType().findFieldByName(fieldName);
		if (fd == null) {
			throw new NoSuchFieldError(
					"Unknown field '" + fieldName + "' for Type '" + msg.getDescriptorForType().getFullName() + "'");
		}
		return fd;
	}

}
