package org.factcast.client.grpc;

import static io.grpc.stub.ClientCalls.*;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
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
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.annotations.VisibleForTesting;

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

    final ProtoConverter converter = new ProtoConverter();

    @Autowired
    GrpcFactStore(AddressChannelFactory channelFactory) {
        this(channelFactory.createChannel(CHANNEL_NAME));

    }

    @VisibleForTesting
    GrpcFactStore(@NonNull Channel channel) {
        this(RemoteFactStoreGrpc.newBlockingStub(channel), RemoteFactStoreGrpc.newStub(channel));
    }

    @VisibleForTesting
    GrpcFactStore(@NonNull RemoteFactStoreBlockingStub newBlockingStub,
            @NonNull RemoteFactStoreStub newStub) {
        this.blockingStub = newBlockingStub;
        this.stub = newStub;
    }

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
        SubscriptionImpl<Fact> subscription = SubscriptionImpl.on(observer);

        StreamObserver<FactStoreProto.MSG_Notification> responseObserver = new ClientStreamObserver(
                subscription);

        ClientCall<MSG_SubscriptionRequest, MSG_Notification> call = stub.getChannel().newCall(
                RemoteFactStoreGrpc.METHOD_SUBSCRIBE, stub.getCallOptions()
                        .withWaitForReady()
                        .withCompression("gzip"));

        asyncServerStreamingCall(call, converter.toProto(req), responseObserver);

        return subscription.onClose(() -> {
            cancel(call);
        });
    }

    private void cancel(final ClientCall<MSG_SubscriptionRequest, MSG_Notification> call) {
        call.cancel("Client is no longer interested", null);
    }

    @Override
    public OptionalLong serialOf(@NonNull UUID l) {
        return converter.fromProto(blockingStub.serialOf(converter.toProto(l)));
    }
}
