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
package org.factcast.client.grpc;

import static io.grpc.stub.ClientCalls.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.store.*;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.*;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.conv.ProtocolVersion;
import org.factcast.grpc.api.conv.ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import io.grpc.*;
import io.grpc.Status.Code;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.Generated;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.devh.boot.grpc.client.security.CallCredentialsHelper;

/**
 * Adapter that implements a FactStore by calling a remote one via GRPC.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */

@Slf4j
public class GrpcFactStore implements FactStore, SmartInitializingSingleton {

    private final CompressionCodecs codecs = new CompressionCodecs();

    private static final String CHANNEL_NAME = "factstore";

    private static final ProtocolVersion PROTOCOL_VERSION = ProtocolVersion.of(1, 1, 0);

    private RemoteFactStoreBlockingStub blockingStub;

    private RemoteFactStoreStub stub;

    private final ProtoConverter converter = new ProtoConverter();

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @Autowired
    @Generated
    public GrpcFactStore(
            FactCastGrpcChannelFactory channelFactory,
            @Value("${grpc.client.factstore.credentials:#{null}}") Optional<String> credentials) {
        this(channelFactory.createChannel(CHANNEL_NAME), credentials);
    }

    @Generated
    @VisibleForTesting
    GrpcFactStore(Channel channel, Optional<String> credentials) {
        this(RemoteFactStoreGrpc.newBlockingStub(channel), RemoteFactStoreGrpc.newStub(channel),
                credentials);
    }

    private GrpcFactStore(
            RemoteFactStoreBlockingStub newBlockingStub, RemoteFactStoreStub newStub,
            Optional<String> credentials) {
        blockingStub = newBlockingStub;
        stub = newStub;

        if (credentials.isPresent()) {
            String[] sa = credentials.get().split(":");
            if (sa.length != 2) {
                throw new IllegalArgumentException(
                        "Credentials in 'grpc.client.factstore.credentials' have to be defined as 'username:password'");
            }
            CallCredentials basic = CallCredentialsHelper.basicAuth(sa[0], sa[1]);
            blockingStub = blockingStub.withCallCredentials(basic);
            stub = stub.withCallCredentials(basic);
        }
    }

    @Override
    public void publish(@NonNull List<? extends Fact> factsToPublish) {
        log.trace("publishing {} facts to remote store", factsToPublish.size());
        List<MSG_Fact> mf = factsToPublish.stream()
                .map(converter::toProto)
                .collect(Collectors.toList());
        MSG_Facts mfs = MSG_Facts.newBuilder().addAllFact(mf).build();
        try {
            blockingStub.publish(mfs);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().equals(Status.UNKNOWN)) {
                throw FactcastRemoteException.from(e);
            } else {
                throw wrapRetryable(e);
            }
        }
    }

    @Override
    public Subscription subscribe(
            @NonNull SubscriptionRequestTO req,
            @NonNull FactObserver observer) {
        SubscriptionImpl subscription = SubscriptionImpl.on(observer);
        StreamObserver<FactStoreProto.MSG_Notification> responseObserver = new ClientStreamObserver(
                subscription);
        ClientCall<MSG_SubscriptionRequest, MSG_Notification> call = stub.getChannel()
                .newCall(RemoteFactStoreGrpc.getSubscribeMethod(), stub.getCallOptions()
                        .withWaitForReady());
        try {
            asyncServerStreamingCall(call, converter.toProto(req), responseObserver);
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
        return subscription.onClose(() -> cancel(call));
    }

    @VisibleForTesting
    void cancel(final ClientCall<MSG_SubscriptionRequest, MSG_Notification> call) {
        // cancel does not need to be retried.
        call.cancel("Client is no longer interested", null);
    }

    @Override
    public OptionalLong serialOf(@NonNull UUID l) {
        MSG_UUID protoMessage = converter.toProto(l);
        MSG_OptionalSerial responseMessage;
        try {
            responseMessage = blockingStub.serialOf(protoMessage);
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
        return converter.fromProto(responseMessage);
    }

    public synchronized void initialize() {
        if (!initialized.getAndSet(true)) {
            log.debug("Invoking handshake");
            Map<String, String> serverProperties;
            ProtocolVersion serverProtocolVersion;
            try {
                ServerConfig cfg = converter.fromProto(blockingStub.handshake(converter.empty()));
                serverProtocolVersion = cfg.version();
                serverProperties = cfg.properties();
            } catch (StatusRuntimeException e) {
                throw wrapRetryable(e);
            }
            logProtocolVersion(serverProtocolVersion);
            logServerVersion(serverProperties);
            configureCompression(serverProperties.get(Capabilities.CODECS.toString()));
        }
    }

    private static void logServerVersion(Map<String, String> serverProperties) {
        String serverVersion = serverProperties.get(Capabilities.FACTCAST_IMPL_VERSION.toString());
        log.info("Server reported implementation version {}", serverVersion);
    }

    private static void logProtocolVersion(ProtocolVersion serverProtocolVersion) {
        if (!PROTOCOL_VERSION.isCompatibleTo(serverProtocolVersion)) {
            throw new IncompatibleProtocolVersions("Apparently, the local Protocol Version "
                    + PROTOCOL_VERSION
                    + " is not compatible with the Server's " + serverProtocolVersion
                    + ". \nPlease choose a compatible GRPC Client to connect to this Server.");
        }
        if (!PROTOCOL_VERSION.equals(serverProtocolVersion)) {
            log.info("Compatible protocol version encountered client={}, server={}",
                    PROTOCOL_VERSION,
                    serverProtocolVersion);
        } else {
            log.info("Matching protocol version {}", serverProtocolVersion);
        }
    }

    @VisibleForTesting
    void configureCompression(String codecListFromServer) {
        codecs.selectFrom(codecListFromServer).ifPresent(c -> {
            log.info("configuring Codec for sending {}", c);
            // configure compression used for sending messages and header
            // to request compressed messages from server
            Metadata meta = new Metadata();
            meta.put(Headers.MESSAGE_COMPRESSION, c);
            blockingStub = MetadataUtils.attachHeaders(blockingStub.withCompression(c), meta);
            stub = MetadataUtils.attachHeaders(stub.withCompression(c), meta);
        });
    }

    @Override
    public synchronized void afterSingletonsInstantiated() {
        initialize();
    }

    @Override
    public Set<String> enumerateNamespaces() {
        MSG_Empty empty = converter.empty();
        MSG_StringSet resp;
        try {
            resp = blockingStub.enumerateNamespaces(empty);
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
        return converter.fromProto(resp);
    }

    @Override
    public Set<String> enumerateTypes(String ns) {
        MSG_String ns_message = converter.toProto(ns);
        MSG_StringSet resp;
        try {
            resp = blockingStub.enumerateTypes(ns_message);
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
        return converter.fromProto(resp);
    }

    @VisibleForTesting
    static RuntimeException wrapRetryable(StatusRuntimeException e) {
        if (e.getStatus().getCode() == Code.UNAVAILABLE) {
            return new RetryableException(e);
        } else {
            return e;
        }
    }

    @Override
    public boolean publishIfUnchanged(
            @NonNull List<? extends Fact> factsToPublish,
            @NonNull Optional<StateToken> token) {

        ConditionalPublishRequest req = new ConditionalPublishRequest(factsToPublish,
                token.map(StateToken::uuid).orElse(null));
        MSG_ConditionalPublishRequest msg = converter.toProto(req);
        try {

            MSG_ConditionalPublishResult r = blockingStub.publishConditional(msg);
            return r.getSuccess();
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
    }

    @Override
    public void invalidate(@NonNull StateToken token) {
        MSG_UUID msg = converter.toProto(token.uuid());
        try {
            blockingStub.invalidate(msg);
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
    }

    @Override
    public StateToken stateFor(@NonNull Collection<UUID> forAggIds, @NonNull Optional<String> ns) {
        StateForRequest req = new StateForRequest(Lists.newArrayList(forAggIds), ns.orElse(null));
        MSG_StateForRequest msg = converter.toProto(req);
        try {
            MSG_UUID result = blockingStub.stateFor(msg);
            return new StateToken(converter.fromProto(result));
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
    }

    @Override
    public long currentTime() {

        MSG_Empty empty = converter.empty();
        MSG_CurrentDatabaseTime resp;
        try {
            resp = blockingStub.currentTime(empty);
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
        return converter.fromProto(resp);
    }

    @Override
    public Optional<Fact> fetchById(UUID id) {
        log.trace("fetching {} from remote store", id);

        MSG_OptionalFact fetchById;
        try {
            fetchById = blockingStub.fetchById(converter.toProto(id));
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
        if (!fetchById.getPresent()) {
            return Optional.empty();
        } else {
            return converter.fromProto(fetchById);
        }
    }

    @Override
    public Optional<Fact> fetchByIdAndVersion(UUID id, int versionExpected) {
        log.trace("fetching {} from remote store as version {}", id, versionExpected);

        MSG_OptionalFact fetchById;
        try {
            fetchById = blockingStub.fetchByIdAndVersion(converter.toProto(id, versionExpected));
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
        if (!fetchById.getPresent()) {
            return Optional.empty();
        } else {
            return converter.fromProto(fetchById);
        }

    }

    @Override
    public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
        log.trace("fetching snapshot {} from remote store", id);

        MSG_OptionalSnapshot snap;
        try {
            snap = blockingStub.getSnapshot(converter.toProto(id));
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
        if (!snap.getPresent()) {
            return Optional.empty();
        } else {
            return converter.fromProto(snap);
        }
    }

    @Override
    public void setSnapshot(@NonNull SnapshotId id, @NonNull UUID state, @NonNull byte[] bytes) {
        log.trace("sending snapshot {} to remote store ({}kb)", id, bytes.length / 1024);

        try {
            val empty = blockingStub.setSnapshot(converter.toProto(id, state, bytes));
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
    }

    @Override
    public void clearSnapshot(@NonNull SnapshotId id) {
        log.trace("clearing snapshot {} in remote store", id);
        try {
            val empty = blockingStub.clearSnapshot(converter.toProto(id));
        } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
        }
    }

}
