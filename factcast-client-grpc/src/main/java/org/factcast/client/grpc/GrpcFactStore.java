package org.factcast.client.grpc;

import static io.grpc.stub.ClientCalls.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.Subscriptions;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Fact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;

import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.devh.springboot.autoconfigure.grpc.client.AddressChannelFactory;

/**
 * Adapter that implements a FactStore by calling a remote one via GRPC.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@Slf4j
class GrpcFactStore implements FactStore {

    static final String CHANNEL_NAME = "factstore";

    final RemoteFactStoreBlockingStub blockingStub;

    final RemoteFactStoreStub stub;

    GrpcFactStore(@NonNull AddressChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel(CHANNEL_NAME);
        blockingStub = RemoteFactStoreGrpc.newBlockingStub(channel);
        stub = RemoteFactStoreGrpc.newStub(channel);
    }

    final ProtoConverter converter = new ProtoConverter();

    @Override
    public Optional<Fact> fetchById(UUID id) {
        log.trace("fetching {} from remote store", id);
        MSG_OptionalFact fetchById = blockingStub.fetchById(converter.toProto(id));
        if (!fetchById.getPresent()) {
            return Optional.empty();
        } else {
            return converter.fromProto(fetchById);
        }
    }

    @Override
    public void publish(@NonNull List<? extends Fact> factsToPublish) {
        try {
            log.trace("publishing {} facts to remote store", factsToPublish.size());
            List<MSG_Fact> mf = factsToPublish.stream().map(converter::toProto).collect(Collectors
                    .toList());
            MSG_Facts mfs = MSG_Facts.newBuilder().addAllFact(mf).build();
            blockingStub.publish(mfs);
        } catch (Exception e) {
            log.warn("failed to publish {} facts: {}", factsToPublish.size(), e);
        }
    }

    @Override
    public Subscription subscribe(@NonNull SubscriptionRequestTO req,
            @NonNull FactObserver observer) {
        SubscriptionImpl<Fact> subscription = Subscriptions.on(observer);

        final MSG_SubscriptionRequest request = converter.toProto(req);
        final StreamObserver<FactStoreProto.MSG_Notification> responseObserver = new StreamObserver<FactStoreProto.MSG_Notification>() {

            @Override
            public void onNext(MSG_Notification f) {

                log.trace("observer got msg: {}", f);

                switch (f.getType()) {
                case Catchup:
                    log.debug("received onCatchup signal");
                    subscription.notifyCatchup();
                    break;
                case Complete:
                    log.debug("received onComplete signal");
                    subscription.notifyComplete();
                    break;
                case Error:
                    log.debug("received onError signal");
                    subscription.notifyError(new RuntimeException("Server-side Error: \n" + f
                            .getError()));
                    break;

                case Fact:
                    subscription.notifyElement(converter.fromProto(f.getFact()));
                    break;

                case Id:
                    // wrap id in a fact
                    subscription.notifyElement(new IdOnlyFact(converter.fromProto(f.getId())));
                    break;

                case UNRECOGNIZED:
                    subscription.notifyError(new RuntimeException(
                            "Unrecognized notification type. THIS IS A BUG!"));
                    break;
                }
            }

            @Override
            public void onError(Throwable t) {
                subscription.notifyError(t);

            }

            @Override
            public void onCompleted() {
                subscription.notifyComplete();
            }
        };

        final ClientCall<MSG_SubscriptionRequest, MSG_Notification> call = stub.getChannel()
                .newCall(RemoteFactStoreGrpc.METHOD_SUBSCRIBE, stub.getCallOptions()
                        .withWaitForReady().withCompression("gzip"));

        asyncServerStreamingCall(call, request, responseObserver);

        return subscription.onClose(() -> {
            cancel(call);
        });
    }

    private void cancel(final ClientCall<MSG_SubscriptionRequest, MSG_Notification> call) {
        call.cancel("Client is no longer interested", null);
    }
}
