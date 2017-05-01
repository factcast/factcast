package org.factcast.server.grpc;

import java.util.function.Function;

import org.factcast.core.Fact;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;

import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FactObserver implementation, that translates observer Events to transport
 * layer messages.
 * 
 * @author <uwe.schaefer@mercateo.com>
 *
 */
@Slf4j
@RequiredArgsConstructor
final class GrpcObserverAdapter implements FactObserver {

    final ProtoConverter converter = new ProtoConverter();

    @NonNull
    final String id;

    @NonNull
    final StreamObserver<MSG_Notification> observer;

    @NonNull
    final Function<Fact, MSG_Notification> projection;

    @Override
    public void onComplete() {
        log.info("{} onComplete – sending complete notification", id);
        observer.onNext(converter.createCompleteNotification());
        tryComplete();
    }

    @Override
    public void onError(Throwable e) {
        log.warn("{} onError – sending Error notification {}", id, e.getMessage());
        observer.onError(e);
        tryComplete();
    }

    private void tryComplete() {
        try {
            observer.onCompleted();
        } catch (Throwable e) {
            log.trace("{} Expected exception on completion {}", id, e.getMessage());
        }
    }

    @Override
    public void onCatchup() {
        log.info("{} onCatchup – sending catchup notification", id);
        observer.onNext(converter.createCatchupNotification());
    }

    @Override
    public void onNext(Fact element) {
        observer.onNext(projection.apply(element));
    }

}