package org.factcast.server.grpc.service;

import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

//TODO document
@Slf4j
abstract class GrpcObserverAdapter implements FactObserver {

    final ProtoConverter converter = new ProtoConverter();

    final FactStoreGrpcService factStoreGrpcService;

    final StreamObserver<MSG_Notification> observer;

    public GrpcObserverAdapter(FactStoreGrpcService factStoreGrpcService,
            StreamObserver<MSG_Notification> grpcObserver) {
        this.factStoreGrpcService = factStoreGrpcService;
        observer = grpcObserver;
    }

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

}