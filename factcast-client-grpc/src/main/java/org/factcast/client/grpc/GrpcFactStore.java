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

import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;

import org.factcast.core.Fact;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.RetryableException;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.grpc.api.Capabilities;
import org.factcast.grpc.api.CompressionCodecs;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.Headers;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.conv.ProtocolVersion;
import org.factcast.grpc.api.conv.ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ConditionalPublishRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ConditionalPublishResult;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_CurrentDatabaseTime;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Empty;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Fact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_FactSpecsJson;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalSerial;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalSnapshot;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_UUID;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreBlockingStub;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreStub;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.google.common.annotations.VisibleForTesting;

import io.grpc.CallCredentials;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.Generated;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.security.CallCredentialsHelper;

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

  /**
   * The version of this grpc client (taking from pom.xml), to send to the server during handshake
   */
  private static final String CLIENT_VERSION = loadClientVersion();

  private static final String UNKNOWN_VERSION = "unknown";

  private RemoteFactStoreBlockingStub blockingStub;

  private RemoteFactStoreStub stub;

  private RemoteFactStoreStub rawStub;

  private RemoteFactStoreBlockingStub rawBlockingStub;
  private final FactCastGrpcClientProperties properties;

  @Nullable private final String clientId;

  private final ProtoConverter converter = new ProtoConverter();

  private final AtomicBoolean initialized = new AtomicBoolean(false);

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

  private GrpcFactStore(
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
    blockingStub = rawBlockingStub;
    stub = rawStub;

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
  }

  @Override
  public void publish(@NonNull List<? extends Fact> factsToPublish) {
    runAndHandle(
        () -> {
          log.trace("publishing {} facts to remote store", factsToPublish.size());
          List<MSG_Fact> mf =
              factsToPublish.stream().map(converter::toProto).collect(Collectors.toList());
          MSG_Facts mfs = MSG_Facts.newBuilder().addAllFact(mf).build();

          blockingStub.publish(mfs);
        });
  }

  // instead of using aspects
  // GrpcGlobalClientInterceptor was tested but did not work as expected
  @VisibleForTesting
  void runAndHandle(@NonNull Runnable block) {
    try {
      block.run();
    } catch (StatusRuntimeException e) {
      throw ClientExceptionHelper.from(e);
    }
  }

  // instead of using aspects
  // GrpcGlobalClientInterceptor was tested but did not work as expected
  @VisibleForTesting
  <T> T callAndHandle(@NonNull Callable<T> block) {
    try {
      return block.call();
    } catch (StatusRuntimeException e) {
      throw ClientExceptionHelper.from(e);
    } catch (Exception e) {
      throw ExceptionHelper.toRuntime(e);
    }
  }

  @Override
  public Subscription subscribe(
      @NonNull SubscriptionRequestTO req, @NonNull FactObserver observer) {
    return callAndHandle(
        () -> {
          SubscriptionImpl subscription = SubscriptionImpl.on(observer);
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
  public OptionalLong serialOf(@NonNull UUID l) {
    return callAndHandle(
        () -> {
          MSG_UUID protoMessage = converter.toProto(l);
          MSG_OptionalSerial responseMessage;
          try {
            responseMessage = blockingStub.serialOf(protoMessage);
          } catch (StatusRuntimeException e) {
            throw wrapRetryable(e);
          }
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
        addClientVersionTo(metadata);
        MSG_ServerConfig handshake =
            MetadataUtils.attachHeaders(blockingStub, metadata).handshake(converter.empty());
        ServerConfig cfg = converter.fromProto(handshake);
        serverProtocolVersion = cfg.version();
        serverProperties = cfg.properties();
      } catch (StatusRuntimeException e) {
        throw wrapRetryable(e);
      }
      logProtocolVersion(serverProtocolVersion);
      logServerVersion(serverProperties);
      configureCompressionAndMetaData(serverProperties.get(Capabilities.CODECS.toString()));
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

  @VisibleForTesting
  void addClientVersionTo(@NonNull Metadata meta) {
    meta.put(Headers.CLIENT_VERSION, CLIENT_VERSION);
  }

  @Override
  public Set<String> enumerateNamespaces() {
    return callAndHandle(
        () -> {
          MSG_Empty empty = converter.empty();
          return converter.fromProto(blockingStub.enumerateNamespaces(empty));
        });
  }

  @Override
  public long countFacts(List<FactSpec> specs) {
    return callAndHandle(
        () -> {
          MSG_FactSpecsJson msg = converter.toProtoFactSpecs(specs);
          FactStoreProto.MSG_Count result = blockingStub.countFacts(msg);
          return converter.fromProto(result);
        });
  }

  @Override
  public Set<String> enumerateTypes(String ns) {
    return callAndHandle(
        () -> {
          return converter.fromProto(blockingStub.enumerateTypes(converter.toProto(ns)));
        });
  }

  @VisibleForTesting
  static RuntimeException wrapRetryable(StatusRuntimeException e) {
    // TODO
    if (e.getStatus().getCode() == Code.UNAVAILABLE) {
      return new RetryableException(e);
    } else {
      return e;
    }
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
  public @NonNull StateToken stateFor(List<FactSpec> specs) {
    return callAndHandle(
        () -> {
          MSG_FactSpecsJson msg = converter.toProtoFactSpecs(specs);
          MSG_UUID result = blockingStub.stateForSpecsJson(msg);
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
  public Optional<Fact> fetchById(UUID id) {
    log.trace("fetching {} from remote store", id);

    return callAndHandle(
        () -> {
          MSG_OptionalFact fetchById;
          fetchById = blockingStub.fetchById(converter.toProto(id));
          return converter.fromProto(fetchById);
        });
  }

  @Override
  public Optional<Fact> fetchByIdAndVersion(UUID id, int versionExpected) {
    log.trace("fetching {} from remote store as version {}", id, versionExpected);
    return callAndHandle(
        () -> {
          MSG_OptionalFact fetchById;
          fetchById = blockingStub.fetchByIdAndVersion(converter.toProto(id, versionExpected));
          return converter.fromProto(fetchById);
        });
  }

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
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
          @NonNull byte[] bytes = snapshot.bytes();
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

    runAndHandle(
        () -> {
          //noinspection ResultOfMethodCallIgnored
          blockingStub.clearSnapshot(converter.toProto(id));
        });
  }

  private static String loadClientVersion() {
    try (InputStream is = ClassLoader.getSystemResourceAsStream("version.properties")) {

      if (is == null) {
        return UNKNOWN_VERSION;
      }

      Properties p = new Properties();
      p.load(is);

      return p.getProperty("version", UNKNOWN_VERSION);

    } catch (IOException e) {
      log.error("Unable to load client version.", e);
      return UNKNOWN_VERSION;
    }
  }
}
