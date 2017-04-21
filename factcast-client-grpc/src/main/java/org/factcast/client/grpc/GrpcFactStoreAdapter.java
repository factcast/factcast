package org.factcast.client.grpc;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.factcast.core.DefaultFactFactory;
import org.factcast.core.Fact;
import org.factcast.core.store.subscription.Subscription;
import org.factcast.core.store.subscription.SubscriptionRequest;
import org.factcast.server.grpc.api.FactObserver;
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

import com.fasterxml.jackson.databind.ObjectMapper;

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
	final ProtoConverter conv = new ProtoConverter(new DefaultFactFactory(new ObjectMapper()));
	final RemoteFactStoreBlockingStub fc = RemoteFactStoreGrpc
			.newBlockingStub(ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext(true).build());

	@Override
	public CompletableFuture<Subscription> subscribeFact(SubscriptionRequest req, FactObserver observer) {
		// TODO centrally manage
		RemoteFactStoreStub fs = RemoteFactStoreGrpc
				.newStub(ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext(true).build());

		CountDownLatch l = new CountDownLatch(1);

		fs.subscribeFact(conv.toProto(req, false), new StreamObserver<FactStoreProto.MSG_Notification>() {

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
					throw new IllegalStateException(
							"ID Notification recieved, where Fact notification is expected, this is a BUG");
				case Error:
					observer.onError(new RuntimeException("TODO unknown error"));// TODO
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
	public CompletableFuture<Subscription> subscribeId(SubscriptionRequest req, IdObserver observer) {
		// TODO centrally manage
		RemoteFactStoreStub fs = RemoteFactStoreGrpc.newStub(ManagedChannelBuilder.forAddress("localhost", 6565)
				.directExecutor().maxInboundMessageSize(32).usePlaintext(true).build());

		CountDownLatch l = new CountDownLatch(1);

		fs.subscribeId(conv.toProto(req, true), new StreamObserver<FactStoreProto.MSG_Notification>() {

			@Override
			public void onNext(MSG_Notification f) {

				switch (f.getType()) {
				case Catchup:
					observer.onCatchup();
					l.countDown();
					break;
				case Complete:
					observer.onComplete();
					break;
				case Id:
					observer.onNext(conv.fromProto(f.getId()));
					break;
				case Fact:
					throw new IllegalStateException(
							"Fact Notification recieved, where ID Notification is expected, this is a BUG");
				case Error:
					observer.onError(new RuntimeException("TODO unknown error"));// TODO
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

		Subscription subscription = () -> {
			// TODO how to cancel?
		};
		return CompletableFuture.completedFuture(subscription);
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

}
