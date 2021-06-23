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
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.devh.boot.grpc.server.service.GrpcService;
import org.factcast.core.Fact;
import org.factcast.core.FactValidationException;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FastForwardTarget;
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
public class FactStoreGrpcService extends RemoteFactStoreImplBase {

  static final ProtocolVersion PROTOCOL_VERSION = ProtocolVersion.of(1, 1, 0);

  static final AtomicLong subscriptionIdStore = new AtomicLong();

  @NonNull final FactStore store;
  @NonNull final GrpcRequestMetadata grpcRequestMetadata;
  @NonNull final GrpcLimitProperties grpcLimitProperties;
  @NonNull final FastForwardTarget ffwdTarget;

  final CompressionCodecs codecs = new CompressionCodecs();

  final ProtoConverter converter = new ProtoConverter();

  @VisibleForTesting
  @Deprecated
  protected FactStoreGrpcService(FactStore store, GrpcRequestMetadata grpcRequestMetadata) {
    this(store, grpcRequestMetadata, new GrpcLimitProperties(), FastForwardTarget.forTest());
  }

  @VisibleForTesting
  @Deprecated
  protected FactStoreGrpcService(
      FactStore store, GrpcRequestMetadata grpcRequestMetadata, GrpcLimitProperties props) {
    this(store, grpcRequestMetadata, props, FastForwardTarget.forTest());
  }

  @VisibleForTesting
  @Deprecated
  protected FactStoreGrpcService(
      FactStore store, GrpcRequestMetadata grpcRequestMetadata, FastForwardTarget target) {
    this(store, grpcRequestMetadata, new GrpcLimitProperties(), target);
  }

  @Override
  @Secured(FactCastAuthority.AUTHENTICATED)
  public void publish(@NonNull MSG_Facts request, StreamObserver<MSG_Empty> responseObserver) {

    List<Fact> facts =
        request.getFactList().stream().map(converter::fromProto).collect(Collectors.toList());

    List<@NonNull String> namespaces =
        facts.stream().map(Fact::ns).distinct().collect(Collectors.toList());

    try {
      assertCanWrite(namespaces);

      final int size = facts.size();
      log.debug("publish {} fact{}", size, size > 1 ? "s" : "");
      log.trace("publish {}", facts);
      store.publish(facts);
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
  public void subscribe(
      MSG_SubscriptionRequest request, StreamObserver<MSG_Notification> responseObserver) {
    SubscriptionRequestTO req = converter.fromProto(request);
    if (grpcLimitProperties.disabled() || subscriptionRequestAccepted(req)) {

      enableResponseCompression(responseObserver);

      List<@NonNull String> namespaces =
          req.specs().stream().map(FactSpec::ns).distinct().collect(Collectors.toList());
      try {
        assertCanRead(namespaces);

        resetDebugInfo(req);
        BlockingStreamObserver<MSG_Notification> resp =
            new BlockingStreamObserver<>(
                req.toString(), (ServerCallStreamObserver) responseObserver);

        Subscription sub =
            store.subscribe(
                req, new GrpcObserverAdapter(req.toString(), resp, grpcRequestMetadata));

        ((ServerCallStreamObserver<MSG_Notification>) responseObserver)
            .setOnCancelHandler(
                () -> {
                  try {
                    log.debug("got onCancel from stream, closing subscription {}", req.debugInfo());
                    sub.close();
                  } catch (Exception e) {
                    log.debug("While closing connection after canel", e);
                  }
                });

      } catch (StatusException e) {
        responseObserver.onError(e);
      }

    } else {
      throw new StatusRuntimeException(Status.RESOURCE_EXHAUSTED);
    }
  }

  private final LoadingCache<String, Bucket> subscriptionTrail =
      CacheBuilder.newBuilder()
          .maximumSize(100000)
          .expireAfterWrite(3, TimeUnit.MINUTES)
          .build(
              new CacheLoader<String, Bucket>() {
                @Override
                public Bucket load(String key) throws Exception {
                  if (key.endsWith("con")) {

                    log.trace("Creating new bucket4j for continous subscription: {}", key);

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

                    log.trace("Creating new bucket4j for catchup subscription: {}", key);

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
            "Client exhausts resources by excessivly (re-)subscribing: fingerprint: {}",
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
      log.trace("enabled response compression");
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
    log.info("handshake properties: {} ", properties);
    return properties;
  }

  @VisibleForTesting
  void retrieveImplementationVersion(HashMap<String, String> properties) {
    String implVersion = "UNKNOWN";
    URL propertiesUrl = getProjectProperties();
    Properties buildProperties = new Properties();
    if (propertiesUrl != null) {
      try (InputStream is = propertiesUrl.openStream(); ) {
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
    return FactStoreGrpcService.class.getResource(
        "/META-INF/maven/org.factcast/factcast-server-grpc/pom.properties");
  }

  private void resetDebugInfo(SubscriptionRequestTO req) {
    String newId = "grpc-sub#" + subscriptionIdStore.incrementAndGet();
    log.debug("subscribing {} for {} defined as {}", newId, req, req.dump());
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
  public void enumerateNamespaces(
      MSG_Empty request, StreamObserver<MSG_StringSet> responseObserver) {
    try {
      Set<String> allNamespaces =
          store.enumerateNamespaces().stream()
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
  public void publishConditional(
      MSG_ConditionalPublishRequest request,
      StreamObserver<MSG_ConditionalPublishResult> responseObserver) {
    try {
      ConditionalPublishRequest req = converter.fromProto(request);

      List<@NonNull String> namespaces =
          req.facts().stream().map(Fact::ns).distinct().collect(Collectors.toList());
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
      String ns = req.ns(); // TODO is this gets null, we're screwed
      StateToken token =
          store.stateFor(
              req.aggIds().stream()
                  .map(id -> FactSpec.ns(ns).aggId(id))
                  .collect(Collectors.toList()));
      responseObserver.onNext(converter.toProto(token.uuid()));
      responseObserver.onCompleted();
    } catch (Throwable e) {
      responseObserver.onError(e);
    }
  }

  @Override
  public void stateForSpecsJson(
      MSG_FactSpecsJson request, StreamObserver<MSG_UUID> responseObserver) {
    List<FactSpec> req = converter.fromProto(request);
    if (!req.isEmpty()) {

      StateToken token = store.stateFor(req);
      responseObserver.onNext(converter.toProto(token.uuid()));
      responseObserver.onCompleted();
    } else {
      responseObserver.onError(
          new IllegalArgumentException(
              "Cannot determine state for empty list of fact specifications"));
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
  public void currentTime(
      MSG_Empty request, StreamObserver<MSG_CurrentDatabaseTime> responseObserver) {
    try {
      responseObserver.onNext(converter.toProto(store.currentTime()));
      responseObserver.onCompleted();
    } catch (Throwable e) {
      responseObserver.onError(e);
    }
  }

  @Override
  @Secured(FactCastAuthority.AUTHENTICATED)
  public void fetchById(MSG_UUID request, StreamObserver<MSG_OptionalFact> responseObserver) {

    UUID fromProto = converter.fromProto(request);
    log.trace("fetchById {}", fromProto);

    doFetchById(
        responseObserver,
        () -> {
          Optional<Fact> fetchById = store.fetchById(fromProto);
          log.trace("fetchById({}) was {}found", fromProto, fetchById.map(f -> "").orElse("NOT "));
          return fetchById;
        });
  }

  interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  private void doFetchById(
      StreamObserver<MSG_OptionalFact> responseObserver, ThrowingSupplier<Optional<Fact>> o) {
    try {
      enableResponseCompression(responseObserver);

      val fetchById = o.get();
      if (fetchById.isPresent()) {
        assertCanRead(fetchById.get().ns());
      }

      responseObserver.onNext(converter.toProto(fetchById));
      responseObserver.onCompleted();
    } catch (Throwable e) {
      responseObserver.onError(e);
    }
  }

  @Override
  public void fetchByIdAndVersion(
      MSG_UUID_AND_VERSION request, StreamObserver<MSG_OptionalFact> responseObserver) {

    IdAndVersion fromProto = converter.fromProto(request);
    log.trace("fetchById {} in version {}", fromProto.uuid(), fromProto.version());

    doFetchById(
        responseObserver,
        () -> {
          Optional<Fact> fetchById =
              store.fetchByIdAndVersion(fromProto.uuid(), fromProto.version());
          log.trace("fetchById({}) was found", fromProto, fetchById.map(f -> "").orElse("NOT "));

          return fetchById;
        });
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

  @Override
  public void clearSnapshot(MSG_SnapshotId request, StreamObserver<MSG_Empty> responseObserver) {
    try {
      store.clearSnapshot(converter.fromProto(request));
      responseObserver.onNext(MSG_Empty.getDefaultInstance());
      responseObserver.onCompleted();
    } catch (Throwable e) {
      log.error("while clearing snapshot", e);
      responseObserver.onError(e);
    }
  }

  @Override
  public void getSnapshot(
      MSG_SnapshotId request, StreamObserver<MSG_OptionalSnapshot> responseObserver) {
    try {
      SnapshotId id = converter.fromProto(request);

      Optional<Snapshot> snapshot = store.getSnapshot(id);

      if (snapshot.isPresent() && !snapshot.get().compressed()) {
        enableResponseCompression(responseObserver);
      }

      responseObserver.onNext(converter.toProtoSnapshot(snapshot));
      responseObserver.onCompleted();
    } catch (Throwable e) {
      log.error("while getting snapshot", e);
      responseObserver.onError(e);
    }
  }

  @Override
  public void setSnapshot(MSG_Snapshot request, StreamObserver<MSG_Empty> responseObserver) {
    try {
      SnapshotId id = converter.fromProto(request.getId());
      UUID state = converter.fromProto(request.getFactId());
      byte[] bytes = converter.fromProto(request.getData());
      boolean compressed = request.getCompressed();

      store.setSnapshot(new Snapshot(id, state, bytes, compressed));

      responseObserver.onNext(MSG_Empty.getDefaultInstance());
      responseObserver.onCompleted();
    } catch (Throwable e) {
      log.error("while setting snapshot", e);
      responseObserver.onError(e);
    }
  }
}
