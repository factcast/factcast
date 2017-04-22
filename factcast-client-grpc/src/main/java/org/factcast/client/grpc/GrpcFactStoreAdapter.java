package org.factcast.client.grpc;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.store.subscription.Subscription;
import org.factcast.core.store.subscription.SubscriptionRequest;
import org.factcast.server.grpc.api.FactObserver;
import org.factcast.server.grpc.api.GenericObserver;
import org.factcast.server.grpc.api.IdObserver;
import org.factcast.server.grpc.api.RemoteFactCast;
import org.factcast.server.grpc.api.conv.ProtoConverter;
import org.factcast.server.grpc.gen.FactStoreProto;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Fact;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Facts;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification;
import org.factcast.server.grpc.gen.RemoteFactStoreGrpc;
import org.factcast.server.grpc.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.server.grpc.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;

import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * POC
 * 
 * @author usr
 *
 */
// TODO cleanup
@RequiredArgsConstructor
@Slf4j
public class GrpcFactStoreAdapter implements RemoteFactCast {

	// TODO inject
	final ProtoConverter conv = new ProtoConverter();
	final RemoteFactStoreBlockingStub fc = RemoteFactStoreGrpc
			.newBlockingStub(ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext(true).build());

	private CompletableFuture<Subscription> subscribeInternal(SubscriptionRequest req,
			@SuppressWarnings("rawtypes") GenericObserver observer) {
		// TODO centrally manage
		RemoteFactStoreStub fs = RemoteFactStoreGrpc
				.newStub(ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext(true).build());

		CountDownLatch l = new CountDownLatch(1);

		fs.subscribe(conv.toProto(req), new StreamObserver<FactStoreProto.MSG_Notification>() {

			@SuppressWarnings("unchecked")
			@Override
			public void onNext(MSG_Notification f) {

				log.trace("observer got msg: " + f);

				switch (f.getType()) {
				case Catchup:
					observer.onCatchup();
					l.countDown();
					break;
				case Complete:
					observer.onComplete();
					break;

				case Fact:
					observer.onNext(conv.fromProto(f.getFact()));
					break;
				case Id:
					observer.onNext(conv.fromProto(f.getId()));
					break;
				case Error:
					observer.onError(new RuntimeException("TODO unknown error "));// TODO
					break;

				case UNRECOGNIZED:
					observer.onError(new RuntimeException("Unrecognized notification type"));
					break;
				}
			}

			@Override
			public void onError(Throwable t) {
				observer.onError(t);

			}

			@Override
			public void onCompleted() {
				observer.onComplete();
			}
		});
		// wait until catchup
		try {
			l.await();
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}

		// TODO how to close?
		return CompletableFuture.completedFuture(() -> {
		});
	}

	@Override
	public Optional<Fact> fetchById(UUID id) {
		MSG_Fact fetchById = fc.fetchById(conv.toProto(id));
		if (!fetchById.getPresent()) {
			return Optional.empty();
		} else {
			return Optional.ofNullable(conv.fromProto(fetchById));
		}
	}

	@Override
	public void publish(Collection<Fact> factsToPublish) {
		List<MSG_Fact> mf = factsToPublish.stream().map(conv::toProto).collect(Collectors.toList());
		MSG_Facts mfs = MSG_Facts.newBuilder().addAllFact(mf).build();
		fc.publish(mfs);
	}

	@RequiredArgsConstructor
	static class ObserverBridge<T> implements GenericObserver<T> {

		private final GenericObserver<T> delegate;
		private final Class<T> type;

		@Override
		public void onNext(Object f) {
			delegate.onNext(type.cast(f));
		}

	}

	@Override
	public CompletableFuture<Subscription> subscribe(SubscriptionRequest req, IdObserver observer) {
		if (!req.idOnly()) {
			throw new IllegalArgumentException();// TODO
		}
		return subscribeInternal(req, new ObserverBridge<UUID>(observer, UUID.class));
	}

	@Override
	public CompletableFuture<Subscription> subscribe(SubscriptionRequest req, FactObserver observer) {
		if (req.idOnly()) {
			throw new IllegalArgumentException();// TODO
		}
		return subscribeInternal(req, new ObserverBridge<Fact>(observer, Fact.class));
	}

}
