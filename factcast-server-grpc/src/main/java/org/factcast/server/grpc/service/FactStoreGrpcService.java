package org.factcast.server.grpc.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.server.grpc.api.conv.ProtoConverter;
import org.factcast.server.grpc.gen.FactStoreProto;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Empty;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Fact;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Facts;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_UUID;
import org.factcast.server.grpc.gen.RemoteFactStoreGrpc.RemoteFactStoreImplBase;

import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.springboot.autoconfigure.grpc.server.GrpcService;

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
@GrpcService(FactStoreProto.class)
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
			log.debug("fetchById({}) was {}found", fromProto, fetchById.map(f -> "").orElse("NOT "));
			responseObserver.onNext(conv.toProto(fetchById));
			responseObserver.onCompleted();
		} catch (Throwable e) {
			responseObserver.onError(e);
		}
	}

	@Override
	public void publish(@NonNull MSG_Facts request, @NonNull StreamObserver<MSG_Empty> responseObserver) {
		List<Fact> facts = request.getFactList().stream().map(conv::fromProto).collect(Collectors.toList());
		final int size = facts.size();
		log.debug("publish {} fact{}", size, size > 1 ? "s" : "");
		log.trace("publish {}", facts);
		try {
			log.trace("store publish {}", facts);
			store.publish(facts);
			log.trace("store publish done");
			responseObserver.onNext(MSG_Empty.getDefaultInstance());
			responseObserver.onCompleted();
		} catch (Throwable e) {
			log.error("Problem while publishing: ", e);

		}
	}

	@RequiredArgsConstructor
	private abstract class ObserverBridge implements FactStoreObserver {

		final StreamObserver<MSG_Notification> observer;

		@Override
		public void onComplete() {
			log.info("onComplete – sending complete notification");
			observer.onNext(conv.toCompleteNotification());
			tryComplete();
		}

		@Override
		public void onError(Throwable e) {
			log.warn("onError – sending Error notification {}", e);
			observer.onError(e);
			tryComplete();
		}

		private void tryComplete() {
			try {
				observer.onCompleted();
			} catch (Throwable e) {
				log.trace("Expected exception on completion: ", e);
			}
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
		log.trace("creating subscription for {}", req);
		final boolean idOnly = req.idOnly();
		final AtomicReference<CompletableFuture<Subscription>> ref = new AtomicReference<>();

		ref.set(store.subscribe(req, new ObserverBridge(responseObserver) {

			@Override
			public void onNext(Fact f) {
				try {
					responseObserver.onNext(idOnly ? conv.toIdNotification(f) : conv.toNotification(f));
				} catch (Throwable e) {
					log.warn("Exception while sending data to stream", e);
					if (ref.get() != null) {
						try {
							ref.get().getNow(() -> {
							}).close();
						} catch (Exception e1) {
							// swallow.
						}
					}
				}
			}
		}));

	}
}
