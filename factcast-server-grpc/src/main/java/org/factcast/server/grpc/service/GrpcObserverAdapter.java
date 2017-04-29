package org.factcast.server.grpc.service;

import java.util.function.Function;

import org.factcast.core.Fact;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;

import io.grpc.stub.StreamObserver;
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

    final StreamObserver<MSG_Notification> observer;

    final Function<Fact, MSG_Notification> projection;

    @Override
    public void onComplete() {
        log.info("onComplete – sending complete notification");
        observer.onNext(converter.toCompleteNotification());
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
            log.trace("Expected exception on completion ", e);
        }
    }

    @Override
    public void onCatchup() {
        log.info("onCatchup – sending catchup notification");
        observer.onNext(converter.toCatchupNotification());
    }

    @Override
    public void onNext(Fact element) {
        observer.onNext(projection.apply(element));
    }

}