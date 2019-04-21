/*
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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import org.factcast.core.*;
import org.factcast.core.store.*;
import org.factcast.core.subscription.*;
import org.factcast.grpc.api.*;
import org.factcast.grpc.api.conv.*;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.*;
import org.factcast.server.grpc.auth.*;
import org.springframework.security.access.annotation.*;

import com.google.common.annotations.*;

import io.grpc.stub.*;

import lombok.*;
import lombok.extern.slf4j.*;
import net.devh.boot.grpc.server.service.*;

/**
 * Service that provides access to an injected FactStore via GRPC.
 * <p>
 * Configure port using {@link GRpcServerProperties}
 *
 * @author uwe.schaefer@mercateo.com
 */
@Slf4j
@RequiredArgsConstructor
@GrpcService
@SuppressWarnings("all")
@Secured(FactCastRole.READ)
public class FactStoreGrpcService extends RemoteFactStoreImplBase {

    static final ProtocolVersion PROTOCOL_VERSION = ProtocolVersion.of(1, 1, 0);

    final FactStore store;

    private final CompressionCodecs codecs = new CompressionCodecs();

    final ProtoConverter converter = new ProtoConverter();

    static final AtomicLong subscriptionIdStore = new AtomicLong();

    @Override
    public void fetchById(MSG_UUID request,
            StreamObserver<MSG_OptionalFact> responseObserver) {
        try {
            enableResponseCompression(responseObserver);

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
    @Secured(FactCastRole.WRITE)
    public void publish(@NonNull MSG_Facts request,
            StreamObserver<MSG_Empty> responseObserver) {
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
            responseObserver.onError(e);
        }
    }

    @Override
    public void subscribe(MSG_SubscriptionRequest request,
            StreamObserver<MSG_Notification> responseObserver) {
        enableResponseCompression(responseObserver);

        SubscriptionRequestTO req = converter.fromProto(request);
        resetDebugInfo(req);
        BlockingStreamObserver<MSG_Notification> resp = new BlockingStreamObserver<>(req.toString(),
                (ServerCallStreamObserver) responseObserver);
        final boolean idOnly = req.idOnly();
        store.subscribe(req, new GrpcObserverAdapter(req.toString(), resp, f -> idOnly ? converter
                .createNotificationFor(f.id()) : converter.createNotificationFor(f)));
    }

    private void enableResponseCompression(StreamObserver<?> responseObserver) {
        // need to be defensive not to break tests passing mocks here.
        if (responseObserver instanceof ServerCallStreamObserver) {
            ServerCallStreamObserver obs = (ServerCallStreamObserver) responseObserver;
            obs.setMessageCompression(true);
        }
    }

    @Override
    public void handshake(MSG_Empty request,
            StreamObserver<MSG_ServerConfig> responseObserver) {
        ServerConfig cfg = ServerConfig.of(PROTOCOL_VERSION, collectProperties());
        responseObserver.onNext(converter.toProto(cfg));
        responseObserver.onCompleted();
    }

    private Map<String, String> collectProperties() {
        HashMap<String, String> properties = new HashMap<>();
        retrieveImplementationVersion(properties);
        properties.put(Capabilities.CODECS.toString(), codecs.available());
        log.info("Handshake properties: {} ", properties);
        return properties;
    }

    @VisibleForTesting
    void retrieveImplementationVersion(HashMap<String, String> properties) {
        String implVersion = "UNKNOWN";
        URL propertiesUrl = getProjectProperties();
        Properties buildProperties = new Properties();
        if (propertiesUrl != null)
            try {
                InputStream is = propertiesUrl.openStream();
                if (is != null) {
                    buildProperties.load(is);
                    String v = buildProperties.getProperty("version");
                    if (v != null)
                        implVersion = v;
                }
            } catch (Exception ignore) {
                // whatever fails when reading the version implies, that the
                // impl Version is
                // "UNKNOWN"
            }
        properties.put(Capabilities.FACTCAST_IMPL_VERSION.toString(), implVersion);
    }

    @VisibleForTesting
    URL getProjectProperties() {
        return FactStoreGrpcService.class.getResource(
                "/META-INF/maven/org.factcast/factcast-server-grpc/pom.properties");
    }

    private void resetDebugInfo(SubscriptionRequestTO req) {
        String newId = "grpc-sub#" + subscriptionIdStore.incrementAndGet();
        log.info("subscribing {} for {} defined as {}", newId, req, req.dump());
        req.debugInfo(newId);
    }

    @Override
    public void serialOf(MSG_UUID request, StreamObserver<MSG_OptionalSerial> responseObserver) {
        OptionalLong serialOf = store.serialOf(converter.fromProto(request));
        responseObserver.onNext(converter.toProto(serialOf));
        responseObserver.onCompleted();
    }

    @Override
    public void enumerateNamespaces(MSG_Empty request,
            StreamObserver<MSG_StringSet> responseObserver) {
        try {
            responseObserver.onNext(converter.toProto(store.enumerateNamespaces()));
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void enumerateTypes(MSG_String request,
            StreamObserver<MSG_StringSet> responseObserver) {

        enableResponseCompression(responseObserver);

        try {
            Set<String> types = store.enumerateTypes(converter.fromProto(
                    request));
            responseObserver.onNext(converter.toProto(types));
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
    }

    @Override
    @Secured(FactCastRole.WRITE)
    public void publishConditional(MSG_ConditionalPublishRequest request,
            StreamObserver<MSG_ConditionalPublishResult> responseObserver) {
        try {
            ConditionalPublishRequest req = converter.fromProto(request);
            boolean result = store.publishIfUnchanged(req.facts(), req.token());
            responseObserver.onNext(converter.toProto(result));
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
    }

    @Override
    @Secured(FactCastRole.WRITE)
    public void stateFor(MSG_StateForRequest request, StreamObserver<MSG_UUID> responseObserver) {
        try {
            StateForRequest req = converter.fromProto(request);
            StateToken token = store.stateFor(req.aggIds(), Optional.ofNullable(req.ns()));
            responseObserver.onNext(converter.toProto(token.uuid()));
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
    }

    @Override
    @Secured(FactCastRole.WRITE)
    public void invalidate(MSG_UUID request, StreamObserver<MSG_Empty> responseObserver) {
        try {
            UUID tokenId = converter.fromProto(request);
            store.invalidate(new StateToken(tokenId));
            responseObserver.onNext(MSG_Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
    }

}
