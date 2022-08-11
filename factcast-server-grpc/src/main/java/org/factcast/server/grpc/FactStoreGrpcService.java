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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.Hashing;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.factcast.core.Fact;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.core.util.FactCastJson;
import org.factcast.core.util.MavenHelper;
import org.factcast.grpc.api.Capabilities;
import org.factcast.grpc.api.CompressionCodecs;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.StateForRequest;
import org.factcast.grpc.api.conv.IdAndVersion;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.conv.ProtocolVersion;
import org.factcast.grpc.api.conv.ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc.RemoteFactStoreImplBase;
import org.factcast.server.grpc.auth.FactCastAuthority;
import org.factcast.server.grpc.auth.FactCastUser;
import org.factcast.server.grpc.metrics.NOPServerMetrics;
import org.factcast.server.grpc.metrics.ServerMetrics;
import org.factcast.server.grpc.metrics.ServerMetrics.OP;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Service that provides access to an injected FactStore via GRPC.
 *
 * <p>Configure port using {@link GRpcServerProperties}
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Slf4j
@RequiredArgsConstructor
@GrpcService
@SuppressWarnings("all")
public class FactStoreGrpcService extends RemoteFactStoreImplBase implements InitializingBean {

  static final ProtocolVersion PROTOCOL_VERSION = ProtocolVersion.of(1, 1, 0);

  static final AtomicLong subscriptionIdStore = new AtomicLong();

  @NonNull final FactStore store;
  @NonNull final GrpcRequestMetadata grpcRequestMetadata;
  @NonNull final GrpcLimitProperties grpcLimitProperties;
  @NonNull final FastForwardTarget ffwdTarget;
  @NonNull final ServerMetrics metrics;

  final CompressionCodecs codecs = new CompressionCodecs();

  final ProtoConverter converter = new ProtoConverter();

  final ServerExceptionLogger serverExceptionLogger = new ServerExceptionLogger();

  @VisibleForTesting
  @Deprecated
  protected FactStoreGrpcService(FactStore store, GrpcRequestMetadata grpcRequestMetadata) {
    this(
        store,
        grpcRequestMetadata,
        new GrpcLimitProperties(),
        FastForwardTarget.forTest(),
        new NOPServerMetrics());
  }

  @VisibleForTesting
  @Deprecated
  protected FactStoreGrpcService(
      FactStore store, GrpcRequestMetadata grpcRequestMetadata, GrpcLimitProperties props) {
    this(store, grpcRequestMetadata, props, FastForwardTarget.forTest(), new NOPServerMetrics());
  }

  @VisibleForTesting
  @Deprecated
  protected FactStoreGrpcService(
      FactStore store, GrpcRequestMetadata grpcRequestMetadata, FastForwardTarget target) {
    this(store, grpcRequestMetadata, new GrpcLimitProperties(), target, new NOPServerMetrics());
  }

  @Override
  @Secured(FactCastAuthority.AUTHENTICATED)
  public void publish(@NonNull MSG_Facts request, StreamObserver<MSG_Empty> responseObserver) {
    initialize(responseObserver);
    List<Fact> facts =
        request.getFactList().stream().map(converter::fromProto).collect(Collectors.toList());

    List<@NonNull String> namespaces =
        facts.stream().map(Fact::ns).distinct().collect(Collectors.toList());

    assertCanWrite(namespaces);

    final int size = facts.size();

    final var clientId = grpcRequestMetadata.clientId();
    if (clientId.isPresent()) {
      final var id = clientId.get();
      facts = facts.stream().map(f -> tagFactSource(f, id)).collect(Collectors.toList());
    }
    log.debug("{}publish {} fact{}", clientIdPrefix(), size, size > 1 ? "s" : "");
    store.publish(facts);
    responseObserver.onNext(MSG_Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @VisibleForTesting
  Fact tagFactSource(@NonNull Fact f, @NonNull String source) {
    try {
      JsonNode h = FactCastJson.readTree(f.jsonHeader());
      ObjectNode meta = (ObjectNode) h.get("meta");
      meta.set("source", TextNode.valueOf(source));
      return Fact.of(h.toString(), f.jsonPayload());
    } catch (JsonProcessingException e) {
      // skip it - this will break later anyway....
      return f;
    }
  }

  private String clientIdPrefix() {
    return grpcRequestMetadata.clientId().map(id -> id + "|").orElse("");
  }

  @Override
  @Secured(FactCastAuthority.AUTHENTICATED)
  public void subscribe(
      MSG_SubscriptionRequest request, StreamObserver<MSG_Notification> responseObserver) {
    SubscriptionRequestTO req = converter.fromProto(request);
    if (grpcLimitProperties.disabled() || subscriptionRequestAccepted(req)) {

      enableResponseCompression(responseObserver);

      List<@NonNull String> namespaces =
          req.specs().stream().map(FactSpec::ns).distinct().collect(Collectors.toList());

      assertCanRead(namespaces);

      resetDebugInfo(req, grpcRequestMetadata);
      BlockingStreamObserver<MSG_Notification> resp =
          new BlockingStreamObserver<>(
              req.toString(),
              (ServerCallStreamObserver) responseObserver,
              grpcRequestMetadata.catchupBatch().orElse(1));

      AtomicReference<Subscription> subRef = new AtomicReference();

      GrpcObserverAdapter observer =
          new GrpcObserverAdapter(
              req.toString(),
              resp,
              grpcRequestMetadata,
              serverExceptionLogger,
              req.keepaliveIntervalInMs());

      final var cancelHandler = new OnCancelHandler(clientIdPrefix(), req, subRef, observer);

      ((ServerCallStreamObserver<MSG_Notification>) responseObserver)
          .setOnCancelHandler(cancelHandler::run);

      Subscription sub = store.subscribe(req, observer);
      subRef.set(sub);

    } else {
      throw new StatusRuntimeException(Status.RESOURCE_EXHAUSTED);
    }
  }

  private void initialize(StreamObserver<?> responseObserver) {
    if (responseObserver instanceof ServerCallStreamObserver)
      ((ServerCallStreamObserver) responseObserver)
          .setOnCancelHandler(
              () -> {
                throw new RequestCanceledByClientException(
                    clientIdPrefix() + "The request was canceled by the client");
              });
  }

  private final LoadingCache<String, Bucket> subscriptionTrail =
      CacheBuilder.newBuilder()
          .maximumSize(100000)
          .expireAfterWrite(3, TimeUnit.MINUTES)
          .softValues()
          .build(
              new CacheLoader<String, Bucket>() {
                @Override
                public Bucket load(String key) throws Exception {
                  if (key.endsWith("con")) {

                    log.trace(
                        "{}Creating new bucket4j for continous subscription: {}",
                        clientIdPrefix(),
                        key);

                    Refill refill =
                        Refill.intervally(
                            grpcLimitProperties.numberOfFollowRequestsAllowedPerClientPerMinute(),
                            Duration.ofMinutes(1));
                    Bandwidth limit =
                        Bandwidth.classic(
                            grpcLimitProperties.initialNumberOfFollowRequestsAllowedPerClient(),
                            refill);
                    return Bucket4j.builder().addLimit(limit).build();
                  } else {

                    log.trace(
                        "{}Creating new bucket4j for catchup subscription: {}",
                        clientIdPrefix(),
                        key);

                    Refill refill =
                        Refill.intervally(
                            grpcLimitProperties.numberOfCatchupRequestsAllowedPerClientPerMinute(),
                            Duration.ofMinutes(1));
                    Bandwidth limit =
                        Bandwidth.classic(
                            grpcLimitProperties.initialNumberOfCatchupRequestsAllowedPerClient(),
                            refill);
                    return Bucket4j.builder().addLimit(limit).build();
                  }
                }
              });

  private boolean subscriptionRequestAccepted(SubscriptionRequestTO request) {
    // if the client progresses, it is considered a different request
    String requestFingerprint =
        request.pid()
            + "|"
            + Hashing.murmur3_32()
                .hashBytes(request.specs().toString().getBytes(StandardCharsets.UTF_8))
            + "|"
            + (request.startingAfter().map(UUID::toString).orElse("-"));
    if (request.continuous()) {
      requestFingerprint = requestFingerprint + "|con";
    } else {
      requestFingerprint = requestFingerprint + "|cat";
    }

    try {
      if (subscriptionTrail.get(requestFingerprint).tryConsume(1)) {
        return true;
      } else {
        log.warn(
            "{}Client exhausts resources by excessivly (re-)subscribing: fingerprint: {}",
            clientIdPrefix(),
            requestFingerprint);
        return false;
      }
    } catch (ExecutionException e) {
      log.error("While finding or creating bucket: ", e);
      return false;
    }
  }

  private void enableResponseCompression(StreamObserver<?> responseObserver) {
    // need to be defensive not to break tests passing mocks here.
    if (responseObserver instanceof ServerCallStreamObserver) {
      ServerCallStreamObserver obs = (ServerCallStreamObserver) responseObserver;
      obs.setMessageCompression(true);
      log.trace("{}enabled response compression", clientIdPrefix());
    }
  }

  @Override
  public void handshake(MSG_Empty request, StreamObserver<MSG_ServerConfig> responseObserver) {
    metrics.timed(
        OP.HANDSHAKE,
        () -> {
          initialize(responseObserver);

          String clientId = Objects.requireNonNull(grpcRequestMetadata.clientIdAsString());
          String clientVersion =
              Objects.requireNonNull(grpcRequestMetadata.clientVersionAsString());

          log.info("Handshake from '{}' using version {}", clientId, clientVersion);
          metrics.count(
              ServerMetrics.EVENT.CLIENT_VERSION, Tags.of(Tag.of(clientId, clientVersion)));

          ServerConfig cfg = ServerConfig.of(PROTOCOL_VERSION, collectProperties());
          responseObserver.onNext(converter.toProto(cfg));
          responseObserver.onCompleted();
        });
  }

  private Map<String, String> collectProperties() {
    HashMap<String, String> properties = new HashMap<>();
    retrieveImplementationVersion(properties);

    String name = grpcRequestMetadata.clientId().orElse("");
    properties.put(Capabilities.CODECS.toString(), codecs.available());
    // since 0.5.2
    properties.put(Capabilities.FAST_STATE_TOKEN.toString(), Boolean.TRUE.toString());

    log.info("{}handshake (serverConfig={})", clientIdPrefix(), properties);
    return properties;
  }

  @VisibleForTesting
  void retrieveImplementationVersion(HashMap<String, String> properties) {
    properties.put(Capabilities.FACTCAST_IMPL_VERSION.toString(), getServerArtifactVersion());
  }

  @NonNull
  private static String getServerArtifactVersion() {
    return MavenHelper.getVersion("factcast-server-grpc", FactStoreGrpcService.class)
        .orElse("UNKNOWN");
  }

  private void resetDebugInfo(SubscriptionRequestTO req, GrpcRequestMetadata meta) {
    String newId = "grpc-sub#" + subscriptionIdStore.incrementAndGet();
    if (meta != null) {
      newId = meta.clientId().map(id -> id + "|").orElse("") + newId;
    }
    log.debug("{}subscribing {} for {} defined as {}", clientIdPrefix(), newId, req, req.dump());
    req.debugInfo(newId);
  }

  @Override
  @Secured(FactCastAuthority.AUTHENTICATED)
  public void serialOf(MSG_UUID request, StreamObserver<MSG_OptionalSerial> responseObserver) {
    initialize(responseObserver);

    OptionalLong serialOf = store.serialOf(converter.fromProto(request));
    responseObserver.onNext(converter.toProto(serialOf));
    responseObserver.onCompleted();
  }

  @Override
  @Secured(FactCastAuthority.AUTHENTICATED)
  public void enumerateNamespaces(
      MSG_Empty request, StreamObserver<MSG_StringSet> responseObserver) {
    initialize(responseObserver);

    Set<String> allNamespaces =
        store.enumerateNamespaces().stream()
            .filter(getFactcastUser()::canRead)
            .collect(Collectors.toSet());

    responseObserver.onNext(converter.toProto(allNamespaces));
    responseObserver.onCompleted();
  }

  @Override
  @Secured(FactCastAuthority.AUTHENTICATED)
  public void enumerateTypes(MSG_String request, StreamObserver<MSG_StringSet> responseObserver) {
    initialize(responseObserver);

    enableResponseCompression(responseObserver);
    String ns = converter.fromProto(request);

    assertCanRead(ns);
    Set<String> types = store.enumerateTypes(ns);
    responseObserver.onNext(converter.toProto(types));
    responseObserver.onCompleted();
  }

  @Override
  @Secured(FactCastAuthority.AUTHENTICATED)
  public void publishConditional(
      MSG_ConditionalPublishRequest request,
      StreamObserver<MSG_ConditionalPublishResult> responseObserver) {
    initialize(responseObserver);

    ConditionalPublishRequest req = converter.fromProto(request);

    List<? extends Fact> facts = req.facts();

    List<@NonNull String> namespaces =
        facts.stream().map(Fact::ns).distinct().collect(Collectors.toList());
    assertCanWrite(namespaces);

    final var clientId = grpcRequestMetadata.clientId();
    if (clientId.isPresent()) {
      final var id = clientId.get();
      facts = facts.stream().map(f -> tagFactSource(f, id)).collect(Collectors.toList());
    }

    boolean result = store.publishIfUnchanged(facts, req.token());
    responseObserver.onNext(converter.toProto(result));
    responseObserver.onCompleted();
  }

  @Override
  @Secured(FactCastAuthority.AUTHENTICATED)
  public void stateFor(MSG_StateForRequest request, StreamObserver<MSG_UUID> responseObserver) {
    initialize(responseObserver);

    StateForRequest req = converter.fromProto(request);
    String ns = req.ns(); // TODO is this gets null, we're screwed
    assertCanRead(ns);
    StateToken token =
        store.stateFor(
            req.aggIds().stream()
                .map(id -> FactSpec.ns(ns).aggId(id))
                .collect(Collectors.toList()));
    responseObserver.onNext(converter.toProto(token.uuid()));
    responseObserver.onCompleted();
  }

  @Override
  public void stateForSpecsJson(
      MSG_FactSpecsJson request, StreamObserver<MSG_UUID> responseObserver) {
    Function<List<FactSpec>, StateToken> tokenSupplier = r -> store.stateFor(r);
    doStateFor(request, responseObserver, tokenSupplier);
  }

  private void doStateFor(
      MSG_FactSpecsJson request,
      StreamObserver<MSG_UUID> responseObserver,
      Function<List<FactSpec>, StateToken> tokenSupplier) {
    initialize(responseObserver);
    List<FactSpec> req = converter.fromProto(request);
    if (!req.isEmpty()) {

      assertCanRead(req.stream().map(FactSpec::ns).collect(Collectors.toList()));

      StateToken token = tokenSupplier.apply(req);
      responseObserver.onNext(converter.toProto(token.uuid()));
      responseObserver.onCompleted();

    } else {
      responseObserver.onError(
          // change type in order to transport?
          new IllegalArgumentException(
              clientIdPrefix() + "Cannot determine state for empty list of fact specifications"));
    }
  }

  @Override
  public void currentStateForSpecsJson(
      MSG_FactSpecsJson request, StreamObserver<MSG_UUID> responseObserver) {
    initialize(responseObserver);
    Function<List<FactSpec>, StateToken> tokenSupplier = r -> store.currentStateFor(r);
    doStateFor(request, responseObserver, tokenSupplier);
  }

  @Override
  @Secured(FactCastAuthority.AUTHENTICATED)
  public void invalidate(MSG_UUID request, StreamObserver<MSG_Empty> responseObserver) {
    initialize(responseObserver);

    UUID tokenId = converter.fromProto(request);
    store.invalidate(new StateToken(tokenId));
    responseObserver.onNext(MSG_Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void currentTime(
      MSG_Empty request, StreamObserver<MSG_CurrentDatabaseTime> responseObserver) {
    initialize(responseObserver);

    responseObserver.onNext(converter.toProto(store.currentTime()));
    responseObserver.onCompleted();
  }

  @Override
  @Secured(FactCastAuthority.AUTHENTICATED)
  public void fetchById(MSG_UUID request, StreamObserver<MSG_OptionalFact> responseObserver) {
    initialize(responseObserver);

    UUID fromProto = converter.fromProto(request);
    log.trace("{}fetchById {}", clientIdPrefix(), fromProto);

    doFetchById(
        responseObserver,
        () -> {
          Optional<Fact> fetchById = store.fetchById(fromProto);
          log.trace(
              "{}fetchById({}) was {}found",
              clientIdPrefix(),
              fromProto,
              fetchById.map(f -> "").orElse("NOT "));
          return fetchById;
        });
  }

  private void doFetchById(
      StreamObserver<MSG_OptionalFact> responseObserver, Supplier<Optional<Fact>> o) {

    enableResponseCompression(responseObserver);

    final var fetchById = o.get();
    if (fetchById.isPresent()) {
      assertCanRead(fetchById.get().ns());
    }

    responseObserver.onNext(converter.toProto(fetchById));
    responseObserver.onCompleted();
  }

  @Override
  public void fetchByIdAndVersion(
      MSG_UUID_AND_VERSION request, StreamObserver<MSG_OptionalFact> responseObserver) {
    initialize(responseObserver);

    IdAndVersion fromProto = converter.fromProto(request);
    log.trace(
        "{}fetchByIdAndVersion {} in version {}",
        clientIdPrefix(),
        fromProto.uuid(),
        fromProto.version());

    doFetchById(
        responseObserver,
        () -> {
          Optional<Fact> fetchById =
              store.fetchByIdAndVersion(fromProto.uuid(), fromProto.version());
          log.trace(
              "{}fetchById({}) was found",
              clientIdPrefix(),
              fromProto,
              fetchById.map(f -> "").orElse("NOT "));

          return fetchById;
        });
  }

  //

  @VisibleForTesting
  protected void assertCanRead(@NonNull String ns) throws StatusRuntimeException {
    FactCastUser user = getFactcastUser();
    if (!user.canRead(ns)) {

      log.warn("{}Not allowed to read from namespace '{}'", clientIdPrefix(), ns);
      throw new StatusRuntimeException(Status.PERMISSION_DENIED, new Metadata());
    }
  }

  @VisibleForTesting
  protected void assertCanRead(List<@NonNull String> namespaces) throws StatusRuntimeException {
    namespaces.forEach(this::assertCanRead);
  }

  @VisibleForTesting
  protected void assertCanWrite(List<@NonNull String> namespaces) throws StatusRuntimeException {
    FactCastUser user = getFactcastUser();
    for (String ns : namespaces) {
      if (!user.canWrite(ns)) {
        log.warn("{}Not allowed to write to namespace '{}'", clientIdPrefix(), ns);
        throw new StatusRuntimeException(Status.PERMISSION_DENIED, new Metadata());
      }
    }
  }

  protected FactCastUser getFactcastUser() throws StatusRuntimeException {
    SecurityContext ctx = SecurityContextHolder.getContext();
    Object authentication = ctx.getAuthentication().getPrincipal();
    if (authentication == null) {
      log.error("{}Authentication is unavailable", clientIdPrefix());
      throw new StatusRuntimeException(Status.PERMISSION_DENIED, new Metadata());
    }
    return (FactCastUser) authentication;
  }

  @Override
  public void clearSnapshot(MSG_SnapshotId request, StreamObserver<MSG_Empty> responseObserver) {
    initialize(responseObserver);

    store.clearSnapshot(converter.fromProto(request));
    responseObserver.onNext(MSG_Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void getSnapshot(
      MSG_SnapshotId request, StreamObserver<MSG_OptionalSnapshot> responseObserver) {
    initialize(responseObserver);

    SnapshotId id = converter.fromProto(request);

    Optional<Snapshot> snapshot = store.getSnapshot(id);

    if (snapshot.isPresent() && !snapshot.get().compressed()) {
      enableResponseCompression(responseObserver);
    }

    responseObserver.onNext(converter.toProtoSnapshot(snapshot));
    responseObserver.onCompleted();
  }

  @Override
  public void setSnapshot(MSG_Snapshot request, StreamObserver<MSG_Empty> responseObserver) {
    initialize(responseObserver);

    SnapshotId id = converter.fromProto(request.getId());
    UUID state = converter.fromProto(request.getFactId());
    byte[] bytes = converter.fromProto(request.getData());
    boolean compressed = request.getCompressed();

    store.setSnapshot(new Snapshot(id, state, bytes, compressed));

    responseObserver.onNext(MSG_Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    log.info("Service version: {}", getServerArtifactVersion());
  }
}
