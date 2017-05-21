package org.factcast.client.grpc;

import org.factcast.core.Fact;
import org.factcast.core.IdOnlyFact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;

import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
class ClientStreamObserver implements StreamObserver<FactStoreProto.MSG_Notification> {

    final ProtoConverter converter = new ProtoConverter();

    @NonNull
    final SubscriptionImpl<Fact> subscription;

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

        default:
            throw new IllegalArgumentException(
                    "Unknown type of notification received! THIS IS A BUG!");
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
}