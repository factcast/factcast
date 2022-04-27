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

import com.google.common.annotations.VisibleForTesting;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import lombok.Generated;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.security.CallCredentialsHelper;
import org.factcast.core.Fact;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.InternalSubscription;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.Capabilities;
import org.factcast.grpc.api.CompressionCodecs;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.Headers;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.conv.ProtocolVersion;
import org.factcast.grpc.api.conv.ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Adapter that implements a FactStore by calling a remote one via GRPC.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Slf4j
public class GrpcFactStore implements FactStore {

  private final CompressionCodecs codecs = new CompressionCodecs();

  private static final String CHANNEL_NAME = "factstore";

  private static final ProtocolVersion PROTOCOL_VERSION = ProtocolVersion.of(1, 1, 0);
  private final Resilience resilience;

  private RemoteFactStoreBlockingStub blockingStub;

  private RemoteFactStoreStub stub;

  private RemoteFactStoreStub rawStub;

  private RemoteFactStoreBlockingStub rawBlockingStub;
  private final FactCastGrpcClientProperties properties;
  @Nullable private final String clientId;

  private final ProtoConverter converter = new ProtoConverter();

  private final AtomicBoolean initialized = new AtomicBoolean(false);
  @VisibleForTesting @Setter private boolean fastStateToken;

  @Autowired
  @Generated
  public GrpcFactStore(
      @NonNull FactCastGrpcChannelFactory channelFactory,
      @NonNull @Value("${grpc.client.factstore.credentials:#{null}}") Optional<String> credentials,
      @NonNull FactCastGrpcClientProperties properties,
      @Nullable String clientId) {
    this(channelFactory.createChannel(CHANNEL_NAME), credentials, properties, clientId);
  }

  @Generated
  GrpcFactStore(
      @NonNull Channel channel,
      @NonNull Optional<String> credentials,
      @NonNull FactCastGrpcClientProperties properties,
      String clientId) {
    this(
        RemoteFactStoreGrpc.newBlockingStub(channel),
        RemoteFactStoreGrpc.newStub(channel),
        credentials,
        properties,
        clientId);
  }

  @Generated
  @VisibleForTesting
  GrpcFactStore(@NonNull Channel channel, @NonNull Optional<String> credentials) {
    this(
        RemoteFactStoreGrpc.newBlockingStub(channel),
        RemoteFactStoreGrpc.newStub(channel),
        credentials,
        new FactCastGrpcClientProperties(),
        null);
  }

  @VisibleForTesting
  GrpcFactStore(
      @NonNull RemoteFactStoreBlockingStub newBlockingStub,
      @NonNull RemoteFactStoreStub newStub,
      @NonNull Optional<String> credentials,
      @NonNull FactCastGrpcClientProperties properties,
      @Nullable String clientId) {
    rawBlockingStub = newBlockingStub;
    rawStub = newStub;
    this.properties = properties;
    this.clientId = clientId;

    // initially use the raw ones...
    blockingStub = rawBlockingStub.withWaitForReady();
    stub = rawStub.withWaitForReady();

    if (credentials.isPresent()) {
      String[] sa = credentials.get().split(":");
      if (sa.length != 2) {
        throw new IllegalArgumentException(
            "Credentials in 'grpc.client.factstore.credentials' have to be defined as"
                + " 'username:password'");
      }
      CallCredentials basic = CallCredentialsHelper.basicAuth(sa[0], sa[1]);
      blockingStub = blockingStub.withCallCredentials(basic);
      stub = stub.withCallCredentials(basic);
    }

    resilience = new Resilience(properties.getResilience());
  }

  @Override
  public void publish(@NonNull List<? extends Fact> factsToPublish) {
    runAndHandle(
        () -> {
          log.trace("publishing {} facts to remote store", factsToPublish.size());
          List<MSG_Fact> mf =
              factsToPublish.stream().map(converter::toProto).collect(Collectors.toList());
          MSG_Facts mfs = MSG_Facts.newBuilder().addAllFact(mf).build();

          //noinspection ResultOfMethodCallIgnored
          blockingStub.publish(mfs);
        });
  }

  // instead of using aspects
  // GrpcGlobalClientInterceptor was tested but did not work as expected
  @VisibleForTesting
  void runAndHandle(@NonNull Runnable block) {
    for (; ; ) {
      try {
        resilience.registerAttempt();
        block.run();
        return;
      } catch (Exception e) {
        if (resilience.shouldRetry(e)) {
          log.warn("Temporary failure", e);
          log.info("Retry call to remote server");
          resilience.sleepForInterval();
          // continue and try next attempt
        } else {
          throw ClientExceptionHelper.from(e);
        }
      }
    }
  }

  // instead of using aspects
  // GrpcGlobalClientInterceptor was tested but did not work as expected
  @VisibleForTesting
  <T> T callAndHandle(@NonNull Callable<T> block) {
    for (; ; ) {
      try {
        resilience.registerAttempt();
        T call = block.call();
        return call;
      } catch (Exception e) {
        if (resilience.shouldRetry(e)) {
          log.warn("Temporary failure", e);
          log.info("Retry call to remote server");
          resilience.sleepForInterval();
          // continue and try next attempt
        } else {
          throw ClientExceptionHelper.from(e);
        }
      }
    }
  }

  @Override
  @NonNull
  public Subscription subscribe(
      @NonNull SubscriptionRequestTO req, @NonNull FactObserver observer) {
    if (properties.getResilience().isEnabled())
      return new ResilientGrpcSubscription(this, req, observer, properties.getResilience());
    else return internalSubscribe(req, observer);
  }

  public Subscription internalSubscribe(
      @NonNull SubscriptionRequestTO req, @NonNull FactObserver observer) {
    return callAndHandle(
        () -> {
          InternalSubscription subscription = SubscriptionImpl.on(observer);
          StreamObserver<FactStoreProto.MSG_Notification> responseObserver =
              new ClientStreamObserver(subscription, req.keepaliveIntervalInMs());
          ClientCall<MSG_SubscriptionRequest, MSG_Notification> call =
              stub.getChannel()
                  .newCall(
                      RemoteFactStoreGrpc.getSubscribeMethod(),
                      stub.getCallOptions().withWaitForReady());
          asyncServerStreamingCall(call, converter.toProto(req), responseObserver);
          return subscription.onClose(() -> cancel(call));
        });
  }

  @VisibleForTesting
  void cancel(ClientCall<MSG_SubscriptionRequest, MSG_Notification> call) {
    // cancel does not need to be retried.
    call.cancel("Client is no longer interested", null);
  }

  @Override
  @NonNull
  public OptionalLong serialOf(@NonNull UUID l) {
    return callAndHandle(
        () -> {
          MSG_UUID protoMessage = converter.toProto(l);
          MSG_OptionalSerial responseMessage;
          responseMessage = blockingStub.serialOf(protoMessage);
          return converter.fromProto(responseMessage);
        });
  }

  @PostConstruct
  public synchronized void initialize() {
    if (!initialized.getAndSet(true)) {
      log.debug("Invoking handshake");
      Map<String, String> serverProperties;
      ProtocolVersion serverProtocolVersion;
      try {
        Metadata metadata = new Metadata();
        addClientIdTo(metadata);
        @SuppressWarnings("deprecation")
        MSG_ServerConfig handshake =
            MetadataUtils.attachHeaders(blockingStub, metadata).handshake(converter.empty());
        ServerConfig cfg = converter.fromProto(handshake);
        serverProtocolVersion = cfg.version();
        serverProperties = cfg.properties();
      } catch (StatusRuntimeException e) {
        throw ClientExceptionHelper.from(e);
      }
      logProtocolVersion(serverProtocolVersion);
      logServerVersion(serverProperties);
      configureCompressionAndMetaData(serverProperties.get(Capabilities.CODECS.toString()));

      this.fastStateToken =
          Optional.ofNullable(serverProperties.get(Capabilities.FAST_STATE_TOKEN.toString()))
              .map(Boolean::parseBoolean)
              .orElse(false);
    }
  }

  private static void logServerVersion(Map<String, String> serverProperties) {
    String serverVersion = serverProperties.get(Capabilities.FACTCAST_IMPL_VERSION.toString());
    log.info("Server reported implementation version {}", serverVersion);
  }

  private static void logProtocolVersion(ProtocolVersion serverProtocolVersion) {
    if (!PROTOCOL_VERSION.isCompatibleTo(serverProtocolVersion)) {
      throw new IncompatibleProtocolVersions(
          "Apparently, the local Protocol Version "
              + PROTOCOL_VERSION
              + " is not compatible with the Server's "
              + serverProtocolVersion
              + ". \nPlease choose a compatible GRPC Client to connect to this Server.");
    }
    if (!PROTOCOL_VERSION.equals(serverProtocolVersion)) {
      log.info(
          "Compatible protocol version encountered client={}, server={}",
          PROTOCOL_VERSION,
          serverProtocolVersion);
    } else {
      log.info("Matching protocol version {}", serverProtocolVersion);
    }
  }

  @VisibleForTesting
  void configureCompressionAndMetaData(String codecListFromServer) {
    codecs
        .selectFrom(codecListFromServer)
        .ifPresent(
            c -> {
              log.info("configuring Codec for sending {}", c);
              // configure compression used for sending messages and header
              // to request compressed messages from server
              Metadata meta = prepareMetaData(c);

              rawBlockingStub = blockingStub;
              rawStub = stub;

              // add compression info
              blockingStub = MetadataUtils.attachHeaders(blockingStub.withCompression(c), meta);
              stub = MetadataUtils.attachHeaders(stub.withCompression(c), meta);
            });
  }

  @VisibleForTesting
  Metadata prepareMetaData(String c) {
    Metadata meta = new Metadata();
    meta.put(Headers.MESSAGE_COMPRESSION, c);

    // existence of this header will enable the fast forward feature
    if (properties.isEnableFastForward()) {
      meta.put(Headers.FAST_FORWARD, "true");
    }

    // existence of this header will enable the on-the-wire-batching feature
    int catchupBatchSize = properties.getCatchupBatchsize();
    if (catchupBatchSize > 1) {
      meta.put(Headers.CATCHUP_BATCHSIZE, String.valueOf(catchupBatchSize));
    }

    addClientIdTo(meta);

    return meta;
  }

  @VisibleForTesting
  void addClientIdTo(@NonNull Metadata meta) {
    if (clientId != null) {
      meta.put(Headers.CLIENT_ID, clientId);
    }
  }

  @Override
  @NonNull
  public Set<String> enumerateNamespaces() {
    return callAndHandle(
        () -> {
          MSG_Empty empty = converter.empty();
          return converter.fromProto(blockingStub.enumerateNamespaces(empty));
        });
  }

  @Override
  @NonNull
  public Set<String> enumerateTypes(@NonNull String ns) {
    return callAndHandle(
        () -> converter.fromProto(blockingStub.enumerateTypes(converter.toProto(ns))));
  }

  @Override
  public boolean publishIfUnchanged(
      @NonNull List<? extends Fact> factsToPublish, @NonNull Optional<StateToken> token) {

    return callAndHandle(
        () -> {
          ConditionalPublishRequest req =
              new ConditionalPublishRequest(
                  factsToPublish, token.map(StateToken::uuid).orElse(null));
          MSG_ConditionalPublishRequest msg = converter.toProto(req);
          MSG_ConditionalPublishResult r = blockingStub.publishConditional(msg);
          return r.getSuccess();
        });
  }

  @Override
  @NonNull
  public StateToken stateFor(@NonNull List<FactSpec> specs) {
    return callAndHandle(
        () -> {
          MSG_FactSpecsJson msg = converter.toProtoFactSpecs(specs);
          MSG_UUID result = blockingStub.stateForSpecsJson(msg);
          return new StateToken(converter.fromProto(result));
        });
  }

  @Override
  @NonNull
  public StateToken currentStateFor(List<FactSpec> specs) {
    if (!this.fastStateToken) return stateFor(specs);
    else
      return callAndHandle(
          () -> {
            MSG_FactSpecsJson msg = converter.toProtoFactSpecs(specs);
            MSG_UUID result = blockingStub.currentStateForSpecsJson(msg);
            return new StateToken(converter.fromProto(result));
          });
  }

  @Override
  public void invalidate(@NonNull StateToken token) {
    runAndHandle(
        () -> {
          MSG_UUID msg = converter.toProto(token.uuid());
          //noinspection ResultOfMethodCallIgnored
          blockingStub.invalidate(msg);
        });
  }

  @Override
  public long currentTime() {
    return callAndHandle(
        () -> {
          MSG_Empty empty = converter.empty();
          MSG_CurrentDatabaseTime resp;
          resp = blockingStub.currentTime(empty);
          return converter.fromProto(resp);
        });
  }

  @Override
  @NonNull
  public Optional<Fact> fetchById(@NonNull UUID id) {
    log.trace("fetching {} from remote store", id);

    return callAndHandle(
        () -> {
          MSG_OptionalFact fetchById;
          fetchById = blockingStub.fetchById(converter.toProto(id));
          return converter.fromProto(fetchById);
        });
  }

  @Override
  @NonNull
  public Optional<Fact> fetchByIdAndVersion(@NonNull UUID id, int versionExpected) {
    log.trace("fetching {} from remote store as version {}", id, versionExpected);
    return callAndHandle(
        () -> {
          MSG_OptionalFact fetchById;
          fetchById = blockingStub.fetchByIdAndVersion(converter.toProto(id, versionExpected));
          return converter.fromProto(fetchById);
        });
  }

  @Override
  @NonNull
  public Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    log.trace("fetching snapshot {} from remote store", id);
    return callAndHandle(
        () -> {
          MSG_OptionalSnapshot snap;
          snap = blockingStub.getSnapshot(converter.toProto(id));
          return converter.fromProto(snap);
        });
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snapshot) {
    runAndHandle(
        () -> {
          @NonNull SnapshotId id = snapshot.id();
          byte[] bytes = snapshot.bytes();
          boolean alreadyCompressed = snapshot.compressed();
          @NonNull UUID state = snapshot.lastFact();

          log.trace("sending snapshot {} to remote store ({}kb)", id, bytes.length / 1024);

          RemoteFactStoreBlockingStub stubToUse =
              alreadyCompressed ? rawBlockingStub : blockingStub;

          //noinspection ResultOfMethodCallIgnored
          stubToUse.setSnapshot(converter.toProto(id, state, bytes, alreadyCompressed));
        });
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    log.trace("clearing snapshot {} in remote store", id);
    //noinspection ResultOfMethodCallIgnored
    runAndHandle(() -> blockingStub.clearSnapshot(converter.toProto(id)));
  }
}
