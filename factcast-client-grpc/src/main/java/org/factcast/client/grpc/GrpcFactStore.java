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
package org.factcast.client.grpc;

import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.Capabilities;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.conv.ProtocolVersion;
import org.factcast.grpc.api.conv.ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Fact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_StringSet;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.annotations.VisibleForTesting;

import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Compressor;
import io.grpc.CompressorRegistry;
import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.devh.springboot.autoconfigure.grpc.client.AddressChannelFactory;

/**
 * Adapter that implements a FactStore by calling a remote one via GRPC.
 *
 * @author uwe.schaefer@mercateo.com
 */
@Slf4j
class GrpcFactStore implements FactStore, SmartInitializingSingleton {

    static final String CHANNEL_NAME = "factstore";

    static final ProtocolVersion PROTOCOL_VERSION = ProtocolVersion.of(1, 0, 0);

    private RemoteFactStoreBlockingStub blockingStub;

    private RemoteFactStoreStub stub;

    private final ProtoConverter converter = new ProtoConverter();

    @SuppressWarnings("FieldCanBeLocal")
    private ProtocolVersion serverProtocolVersion;

    private Map<String, String> serverProperties;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

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
        log.trace("publishing {} facts to remote store", factsToPublish.size());
        List<MSG_Fact> mf = factsToPublish.stream().map(converter::toProto).collect(Collectors
                .toList());
        MSG_Facts mfs = MSG_Facts.newBuilder().addAllFact(mf).build();
        // blockingStub.getCallOptions().withCompression(compressor);
        blockingStub.publish(mfs);
    }

    @Override
    public Subscription subscribe(@NonNull SubscriptionRequestTO req,
            @NonNull FactObserver observer) {
        SubscriptionImpl<Fact> subscription = SubscriptionImpl.on(observer);
        StreamObserver<FactStoreProto.MSG_Notification> responseObserver = new ClientStreamObserver(
                subscription);
        ClientCall<MSG_SubscriptionRequest, MSG_Notification> call = stub.getChannel().newCall(
                RemoteFactStoreGrpc.getSubscribeMethod(), stub.getCallOptions().withWaitForReady());
        asyncServerStreamingCall(call, converter.toProto(req), responseObserver);
        return subscription.onClose(() -> cancel(call));
    }

    private void cancel(final ClientCall<MSG_SubscriptionRequest, MSG_Notification> call) {
        call.cancel("Client is no longer interested", null);
    }

    @Override
    public OptionalLong serialOf(@NonNull UUID l) {
        return converter.fromProto(blockingStub.serialOf(converter.toProto(l)));
    }

    public synchronized void initialize() {
        if (initialized.getAndSet(true))
            initialize();
        log.debug("Invoking handshake");
        ServerConfig cfg = converter.fromProto(blockingStub.handshake(converter.empty()));
        serverProtocolVersion = cfg.version();
        serverProperties = cfg.properties();
        logProtocolVersion(serverProtocolVersion);
        logServerVersion(serverProperties);
        configure();
    }

    private static void logServerVersion(Map<String, String> serverProperties) {
        String serverVersion = serverProperties.get(Capabilities.FACTCAST_IMPL_VERSION.toString());
        log.info("Server reported implementation version {}", serverVersion);
    }

    private static void logProtocolVersion(ProtocolVersion serverProtocolVersion) {
        if (!PROTOCOL_VERSION.isCompatibleTo(serverProtocolVersion))
            throw new IncompatibleProtocolVersions("Apparently, the local Protocol Version "
                    + PROTOCOL_VERSION + " is not compatible with the Server's "
                    + serverProtocolVersion
                    + ". \nPlease choose a compatible GRPC Client to connect to this Server.");
        if (!PROTOCOL_VERSION.equals(serverProtocolVersion))
            log.info("Compatible protocol version encountered client={}, server={}",
                    PROTOCOL_VERSION, serverProtocolVersion);
        else
            log.info("Matching protocol version encountered {}", serverProtocolVersion);
    }

    private void configure() {
        if (!configureLZ4())
            configureGZip();
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean configureGZip() {
        Compressor gzip = CompressorRegistry.getDefaultInstance().lookupCompressor("gzip");
        if (gzip != null) {
            log.info("configuring GZip");
            String encoding = gzip.getMessageEncoding();
            this.blockingStub = blockingStub.withCompression(encoding);
            this.stub = stub.withCompression(encoding);
            return true;
        } else
            return false;
    }

    private boolean configureLZ4() {
        Compressor lz4Compressor = CompressorRegistry.getDefaultInstance().lookupCompressor("lz4");
        boolean localLz4 = lz4Compressor != null;
        boolean remoteLz4 = Boolean.valueOf(serverProperties.get(Capabilities.CODEC_LZ4
                .toString()));
        if (localLz4 && remoteLz4) {
            log.info("LZ4 Codec available on client and server - configuring LZ4");
            String encoding = lz4Compressor.getMessageEncoding();
            this.blockingStub = blockingStub.withCompression(encoding);
            this.stub = stub.withCompression(encoding);
            return true;
        } else
            return false;
    }

    @Override
    public synchronized void afterSingletonsInstantiated() {
        initialize();
    }

    @Override
    public Set<String> enumerateNamespaces() {
        MSG_StringSet set = blockingStub.enumerateNamespaces(converter.empty());
        return converter.fromProto(set);
    }

    @Override
    public Set<String> enumerateTypes(String ns) {
        MSG_StringSet set = blockingStub.enumerateTypes(converter.toProto(ns));
        return converter.fromProto(set);
    }
}
