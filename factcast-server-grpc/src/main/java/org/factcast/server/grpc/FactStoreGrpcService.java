/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.server.grpc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.conv.ProtocolVersion;
import org.factcast.grpc.api.gen.FactStoreProto;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Empty;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ProtocolVersion;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_UUID;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreImplBase;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
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
 * @author uwe.schaefer@mercateo.com
 *
 */
@Slf4j
@RequiredArgsConstructor
@GrpcService(FactStoreProto.class)
@SuppressWarnings("all")
public class FactStoreGrpcService extends RemoteFactStoreImplBase {
    final static ProtocolVersion PROTOCOL_VERSION = ProtocolVersion.of(1, 0, 0);

    final FactStore store;

    final ProtoConverter converter = new ProtoConverter();

    static final AtomicLong subscriptionIdStore = new AtomicLong();

    @Override
    public void fetchById(@NonNull MSG_UUID request,
            @NonNull StreamObserver<MSG_OptionalFact> responseObserver) {
        try {
            UUID fromProto = converter.fromProto(request);
            log.trace("fetchById {}", fromProto);
            Optional<Fact> fetchById = store.fetchById(fromProto);
            log.debug("fetchById({}) was {}found", fromProto, fetchById.map(f -> "")
                    .orElse(
                            "NOT "));
            responseObserver.onNext(converter.toProto(fetchById));
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void publish(@NonNull MSG_Facts request,
            @NonNull StreamObserver<MSG_Empty> responseObserver) {
        List<Fact> facts = request.getFactList()
                .stream()
                .map(converter::fromProto)
                .collect(
                        Collectors.toList());
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
            responseObserver.onError(new StatusRuntimeException(Status.INTERNAL.withDescription(e
                    .getMessage())));
        }
    }

    @Override
    public void subscribe(@NonNull MSG_SubscriptionRequest request,
            @NonNull StreamObserver<MSG_Notification> responseObserver) {
        SubscriptionRequestTO req = converter.fromProto(request);
        resetDebugInfo(req);

        BlockingStreamObserver<MSG_Notification> resp = new BlockingStreamObserver<>(req.toString(),
                (ServerCallStreamObserver) responseObserver);

        final boolean idOnly = req.idOnly();

        store.subscribe(req, new GrpcObserverAdapter(req.toString(), resp, f -> idOnly ? converter
                .createNotificationFor(f.id()) : converter.createNotificationFor(f)));
    }

    @Override
    public void protocolVersion(MSG_Empty request,
            StreamObserver<MSG_ProtocolVersion> responseObserver) {
        responseObserver.onNext(converter.toProto(PROTOCOL_VERSION));
        responseObserver.onCompleted();
    }

    private void resetDebugInfo(@NonNull SubscriptionRequestTO req) {
        String newId = "grpc-sub#" + subscriptionIdStore.incrementAndGet();
        log.info("subscribing {} for {} defined as {}", newId, req, req.dump());
        req.debugInfo(newId);
    }
}
