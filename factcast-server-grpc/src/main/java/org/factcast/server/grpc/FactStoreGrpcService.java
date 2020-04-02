/*
 * Copyright © 2017-2020 factcast.org
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

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.FactValidationException;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.grpc.api.Capabilities;
import org.factcast.grpc.api.CompressionCodecs;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.StateForRequest;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.conv.ProtocolVersion;
import org.factcast.grpc.api.conv.ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ConditionalPublishRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ConditionalPublishResult;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_CurrentDatabaseTime;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Empty;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalSerial;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_StateForRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_String;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_StringSet;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_UUID;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreImplBase;
import org.factcast.server.grpc.auth.FactCastAuthority;
import org.factcast.server.grpc.auth.FactCastUser;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.google.common.annotations.VisibleForTesting;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

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

public class FactStoreGrpcService extends RemoteFactStoreImplBase {

    static final ProtocolVersion PROTOCOL_VERSION = ProtocolVersion.of(1, 1, 0);

    final FactStore store;

    private final CompressionCodecs codecs = new CompressionCodecs();

    final ProtoConverter converter = new ProtoConverter();

    static final AtomicLong subscriptionIdStore = new AtomicLong();

    @Override
    @Secured(FactCastAuthority.AUTHENTICATED)
    public void fetchById(MSG_UUID request, StreamObserver<MSG_OptionalFact> responseObserver) {
        try {
            enableResponseCompression(responseObserver);

            UUID fromProto = converter.fromProto(request);
            log.trace("fetchById {}", fromProto);
            Optional<Fact> fetchById = store.fetchById(fromProto);
            log.debug("fetchById({}) was {}found", fromProto, fetchById.map(f -> "")
                    .orElse("NOT "));

            if (fetchById.isPresent())
                assertCanRead(fetchById.get().ns());

            responseObserver.onNext(converter.toProto(fetchById));
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
    }

    @Override
    @Secured(FactCastAuthority.AUTHENTICATED)
    public void publish(@NonNull MSG_Facts request, StreamObserver<MSG_Empty> responseObserver) {
        List<Fact> facts = request.getFactList()
                .stream()
                .map(converter::fromProto)
                .collect(Collectors.toList());

        List<@NonNull String> namespaces = facts.stream()
                .map(Fact::ns)
                .distinct()
                .collect(Collectors.toList());

        try {
            assertCanWrite(namespaces);

            final int size = facts.size();
            log.debug("publish {} fact{}", size, size > 1 ? "s" : "");
            log.trace("publish {}", facts);
            log.trace("store publish {}", facts);
            store.publish(facts);
            log.trace("store publish done");
            responseObserver.onNext(MSG_Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (FactValidationException e) {
            // no logging here. maybe metrics?
            responseObserver.onError(FactcastRemoteException.of(e));
        } catch (Throwable e) {
            log.error("Problem while publishing: ", e);
            responseObserver.onError(e);
        }
    }

    @Override
    @Secured(FactCastAuthority.AUTHENTICATED)
    public void subscribe(MSG_SubscriptionRequest request,
            StreamObserver<MSG_Notification> responseObserver) {
        enableResponseCompression(responseObserver);

        SubscriptionRequestTO req = converter.fromProto(request);

        List<@NonNull String> namespaces = req.specs()
                .stream()
                .map(FactSpec::ns)
                .distinct()
                .collect(Collectors.toList());
        try {
            assertCanRead(namespaces);

            resetDebugInfo(req);
            BlockingStreamObserver<MSG_Notification> resp = new BlockingStreamObserver<>(
                    req.toString(),
                    (ServerCallStreamObserver) responseObserver);
            final boolean idOnly = req.idOnly();
            store.subscribe(req, new GrpcObserverAdapter(req.toString(), resp,
                    f -> idOnly ? converter.createNotificationFor(f.id())
                            : converter.createNotificationFor(f)));

        } catch (StatusException e) {
            responseObserver.onError(e);
        }

    }

    private void enableResponseCompression(StreamObserver<?> responseObserver) {
        // need to be defensive not to break tests passing mocks here.
        if (responseObserver instanceof ServerCallStreamObserver) {
            ServerCallStreamObserver obs = (ServerCallStreamObserver) responseObserver;
            obs.setMessageCompression(true);
        }
    }

    @Override
    public void handshake(MSG_Empty request, StreamObserver<MSG_ServerConfig> responseObserver) {
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
        if (propertiesUrl != null) {
            try {
                InputStream is = propertiesUrl.openStream();
                if (is != null) {
                    buildProperties.load(is);
                    String v = buildProperties.getProperty("version");
                    if (v != null) {
                        implVersion = v;
                    }
                }
            } catch (Exception ignore) {
                // whatever fails when reading the version implies, that the
                // impl Version is
                // "UNKNOWN"
            }
        }
        properties.put(Capabilities.FACTCAST_IMPL_VERSION.toString(), implVersion);
    }

    @VisibleForTesting
    URL getProjectProperties() {
        return FactStoreGrpcService.class
                .getResource("/META-INF/maven/org.factcast/factcast-server-grpc/pom.properties");
    }

    private void resetDebugInfo(SubscriptionRequestTO req) {
        String newId = "grpc-sub#" + subscriptionIdStore.incrementAndGet();
        log.info("subscribing {} for {} defined as {}", newId, req, req.dump());
        req.debugInfo(newId);
    }

    @Override
    @Secured(FactCastAuthority.AUTHENTICATED)
    public void serialOf(MSG_UUID request, StreamObserver<MSG_OptionalSerial> responseObserver) {
        OptionalLong serialOf = store.serialOf(converter.fromProto(request));
        responseObserver.onNext(converter.toProto(serialOf));
        responseObserver.onCompleted();
    }

    @Override
    @Secured(FactCastAuthority.AUTHENTICATED)
    public void enumerateNamespaces(MSG_Empty request,
            StreamObserver<MSG_StringSet> responseObserver) {
        try {
            Set<String> allNamespaces = store.enumerateNamespaces()
                    .stream()
                    .filter(getFactcastUser()::canRead)
                    .collect(Collectors.toSet());

            responseObserver.onNext(converter.toProto(allNamespaces));
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
    }

    @Override
    @Secured(FactCastAuthority.AUTHENTICATED)
    public void enumerateTypes(MSG_String request, StreamObserver<MSG_StringSet> responseObserver) {

        enableResponseCompression(responseObserver);
        String ns = converter.fromProto(request);

        try {
            assertCanRead(ns);
            Set<String> types = store.enumerateTypes(ns);
            responseObserver.onNext(converter.toProto(types));
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
    }

    @Override
    @Secured(FactCastAuthority.AUTHENTICATED)
    public void publishConditional(MSG_ConditionalPublishRequest request,
            StreamObserver<MSG_ConditionalPublishResult> responseObserver) {
        try {
            ConditionalPublishRequest req = converter.fromProto(request);

            List<@NonNull String> namespaces = req.facts()
                    .stream()
                    .map(Fact::ns)
                    .distinct()
                    .collect(Collectors.toList());
            assertCanWrite(namespaces);

            boolean result = store.publishIfUnchanged(req.facts(), req.token());
            responseObserver.onNext(converter.toProto(result));
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
    }

    @Override
    @Secured(FactCastAuthority.AUTHENTICATED)
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
    @Secured(FactCastAuthority.AUTHENTICATED)
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

    @Override
    public void currentTime(MSG_Empty request,
            StreamObserver<MSG_CurrentDatabaseTime> responseObserver) {
        try {
            responseObserver.onNext(converter.toProto(store.currentTime()));
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(e);
        }

    }

    //

    @VisibleForTesting
    protected void assertCanRead(@NonNull String ns) throws StatusException {
        FactCastUser user = getFactcastUser();
        if (!user.canRead(ns)) {

            log.error("Not allowed to read from namespace '" + ns + "'");
            throw new StatusException(Status.PERMISSION_DENIED, new Metadata());

        }
    }

    @VisibleForTesting
    protected void assertCanRead(List<@NonNull String> namespaces) throws StatusException {
        FactCastUser user = getFactcastUser();
        for (String ns : namespaces) {
            if (!user.canRead(ns)) {
                log.error("Not allowed to read from namespace '" + ns + "'");
                throw new StatusException(Status.PERMISSION_DENIED, new Metadata());
            }
        }
    }

    @VisibleForTesting
    protected void assertCanWrite(List<@NonNull String> namespaces) throws StatusException {
        FactCastUser user = getFactcastUser();
        for (String ns : namespaces) {
            if (!user.canWrite(ns)) {
                log.error("Not allowed to write to namespace '" + ns + "'");
                throw new StatusException(Status.PERMISSION_DENIED, new Metadata());
            }
        }
    }

    protected FactCastUser getFactcastUser() throws StatusException {
        SecurityContext ctx = SecurityContextHolder.getContext();
        Object authentication = ctx.getAuthentication().getPrincipal();
        if (authentication == null) {
            log.error("Authentication is unavailable");
            throw new StatusException(Status.PERMISSION_DENIED, new Metadata());
        }
        return (FactCastUser) authentication;
    }

}
