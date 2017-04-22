package org.factcast.server.grpc.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.server.grpc.api.conv.ProtoConverter;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Empty;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Fact;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Facts;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_UUID;
import org.factcast.server.grpc.gen.RemoteFactStoreGrpc.RemoteFactStoreImplBase;
import org.lognet.springboot.grpc.GRpcService;
import org.lognet.springboot.grpc.autoconfigure.GRpcServerProperties;

import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that provides access to an injected FactStore via GRPC.
 * 
 * Configure port using {@link GRpcServerProperties}
 * 
 * @author usr
 *
 */
@Slf4j
@RequiredArgsConstructor
@GRpcService()
@SuppressWarnings("all")
public class FactStoreGrpcService extends RemoteFactStoreImplBase {

	private final FactStore store;
	private final ProtoConverter conv = new ProtoConverter();

	@Override
	public void fetchById(MSG_UUID request, StreamObserver<MSG_Fact> responseObserver) {
		try {
			UUID fromProto = conv.fromProto(request);
			log.trace("fetchById {}", fromProto);
			Optional<Fact> fetchById = store.fetchById(fromProto);
			log.trace("fetchById {} was {}found", fromProto, fetchById.map(f -> "").orElse("NOT "));
			responseObserver.onNext(conv.toProto(fetchById));
			responseObserver.onCompleted();
		} catch (Throwable e) {
			responseObserver.onError(e);
		}
	}

	@Override
	public void publish(@NonNull MSG_Facts request, @NonNull StreamObserver<MSG_Empty> responseObserver) {
		List<Fact> facts = request.getFactList().stream().map(conv::fromProto).collect(Collectors.toList());
		log.trace("publish {}", facts);
		try {
			log.trace("store publish {}", facts);
			store.publish(facts);
			log.trace("store publish done");
			responseObserver.onNext(MSG_Empty.getDefaultInstance());
			responseObserver.onCompleted();
		} catch (Throwable e) {
			log.error("Problem while publishing: ", e);
			responseObserver.onError(e);
		}
	}

	@RequiredArgsConstructor
	private abstract class ObserverBridge implements FactStoreObserver {

		final StreamObserver<MSG_Notification> observer;

		@Override
		public void onComplete() {
			log.info("onComplete – sending complete notification");
			observer.onNext(conv.toCompleteNotification());
			observer.onCompleted();
		}

		@Override
		public void onError(Throwable e) {
			log.warn("onError – sending Error notification {}", e);
			observer.onError(e);
			observer.onCompleted(); // TODO really?
		}

		@Override
		public void onCatchup() {
			log.info("onCatchup – sending catchup notification");
			observer.onNext(conv.toCatchupNotification());
		}

	}

	@Override
	public void subscribe(MSG_SubscriptionRequest request, StreamObserver<MSG_Notification> responseObserver) {
		SubscriptionRequestTO req = conv.fromProto(request);
		final boolean idOnly = req.idOnly();
		store.subscribe(req, new ObserverBridge(responseObserver) {

			@Override
			public void onNext(Fact f) {
				responseObserver.onNext(idOnly ? conv.toIdNotification(f) : conv.toNotification(f));
			}
		});
	}
}
