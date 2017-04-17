package org.factcast.server.grpc.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

	public MSG_UUID toProto(UUID t) {
		return MSG_UUID.newBuilder().setLsb(t.getLeastSignificantBits()).setMsb(t.getMostSignificantBits()).build();
	}

	public MSG_FactSpec toProto(FactSpec spec) {
		org.factcast.server.grpc.gen.FactStoreProto.MSG_FactSpec.Builder b = MSG_FactSpec.newBuilder();
		if (spec.aggId() != null) {
			b.setAggId(toProto(spec.aggId()));
		}
		b.setNs(spec.ns());
		if (spec.type() != null) {
			b.setType(spec.type());
		}
		if (spec.jsFilterScript() != null) {
			b.setJsFilter(spec.jsFilterScript());
		}

		MSG_FactSpec msg_spec = b.build();
		return msg_spec;
	}

	public FactSpec fromProto(MSG_FactSpec spec) {

		String ns = spec.getNs();
		String type = spec.getType();
		UUID aggId = fromProto(spec.getAggId());
		String jsFilter = spec.getJsFilter();

		FactSpec s = FactSpec.ns(ns);
		if ((type != null) && (type.trim().length() > 0)) {
			s.type(type);
		}
		if ((aggId != null) && (!((aggId.getLeastSignificantBits() == 0) && (aggId.getMostSignificantBits() == 0)))) {
			s.aggId(aggId);
		}
		if ((jsFilter != null) && (jsFilter.trim().length() > 0)) {
			s.jsFilterScript(jsFilter);
		}

		return s;
	}

	public SubscriptionRequest fromProto(MSG_SubscriptionRequest request) {
		long maxLatency = Math.min(request.getMaxLatency(), 10);// TODO

		boolean follow = request.getContinous();
		List<FactSpec> specs = request.getSpecsList().stream().map(this::fromProto).collect(Collectors.toList());
		FactSpec first = specs.remove(0);

		SpecBuilder r = follow ? SubscriptionRequest.follow(maxLatency, first) : SubscriptionRequest.catchup(first);
		specs.forEach(s -> r.or(s));

		MSG_UUID since = request.getSince();

		SubscriptionRequest finalRequest;
		if ((since != null) && (!((since.getLsb() == 0) && (since.getMsb() == 0)))) {
			finalRequest = r.since(fromProto(since));
		} else {
			finalRequest = r.sinceInception();
		}
		return finalRequest;
	}

	public MSG_SubscriptionRequest toProto(SubscriptionRequest request, boolean idOnly) {
		long maxLatency = Math.min(request.maxLatencyInMillis(), 10);// TODO

		boolean follow = request.continous();
		List<MSG_FactSpec> specs = request.specs().stream().map(this::toProto).collect(Collectors.toList());

		org.factcast.server.grpc.gen.FactStoreProto.MSG_SubscriptionRequest.Builder b = MSG_SubscriptionRequest
				.newBuilder();
		b.setIdOnly(idOnly);
		b.setContinous(follow);
		b.setMaxLatency(maxLatency);
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

	public UUID fromIdNotification(MSG_Notification n) {
		if (n.getType() != MSG_Notification.Type.Id) {
			throw new IllegalArgumentException("Wrong notificaiton type");
		}

		return fromProto(n.getId());
	}

}
