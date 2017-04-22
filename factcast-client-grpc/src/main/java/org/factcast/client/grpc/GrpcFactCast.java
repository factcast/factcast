package org.factcast.client.grpc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.subscription.FactObserver;
import org.factcast.core.subscription.GenericObserver;
import org.factcast.core.subscription.IdObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.server.grpc.api.conv.ProtoConverter;
import org.factcast.server.grpc.gen.FactStoreProto;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Fact;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Facts;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification;
import org.factcast.server.grpc.gen.RemoteFactStoreGrpc;
import org.factcast.server.grpc.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.server.grpc.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;
import org.springframework.stereotype.Component;

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.springboot.autoconfigure.grpc.client.AddressChannelFactory;

/**
 * POC
 * 
 * @author usr
 *
 */
// TODO cleanup

@Slf4j
@Component
public class GrpcFactCast implements FactCast {

	private final AddressChannelFactory channelFactory;
	private final RemoteFactStoreBlockingStub blockingStub;
	private final RemoteFactStoreStub stub;

	GrpcFactCast(AddressChannelFactory channelFactory) {
		this.channelFactory = channelFactory;

		Channel c = channelFactory.createChannel("factstore");
		blockingStub = RemoteFactStoreGrpc.newBlockingStub(c);
		stub = RemoteFactStoreGrpc.newStub(c);

	}

	final ProtoConverter conv = new ProtoConverter();

	private CompletableFuture<Subscription> subscribeInternal(SubscriptionRequestTO req,
			@SuppressWarnings("rawtypes") GenericObserver observer) {
		// TODO centrally manage

		CountDownLatch l = new CountDownLatch(1);

		stub.subscribe(conv.toProto(req), new StreamObserver<FactStoreProto.MSG_Notification>() {

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

		MSG_Fact fetchById = blockingStub.fetchById(conv.toProto(id));
		if (!fetchById.getPresent()) {
			return Optional.empty();
		} else {
			return Optional.ofNullable(conv.fromProto(fetchById));
		}
	}

	@Override
	public void publish(List<Fact> factsToPublish) {
		List<MSG_Fact> mf = factsToPublish.stream().map(conv::toProto).collect(Collectors.toList());
		MSG_Facts mfs = MSG_Facts.newBuilder().addAllFact(mf).build();
		blockingStub.publish(mfs);
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
	public CompletableFuture<Subscription> subscribeToIds(SubscriptionRequest req, IdObserver observer) {
		return subscribeInternal(SubscriptionRequestTO.forIds(req), new ObserverBridge<UUID>(observer, UUID.class));
	}

	@Override
	public CompletableFuture<Subscription> subscribeToFacts(SubscriptionRequest req, FactObserver observer) {
		return subscribeInternal(SubscriptionRequestTO.forFacts(req), new ObserverBridge<Fact>(observer, Fact.class));
	}

}
