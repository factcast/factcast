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
package org.factcast.grpc.api.conv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.ByteString;
import com.google.protobuf.ProtocolStringList;
import java.util.*;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.util.FactCastJson;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.StateForRequest;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification.Type;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact.Builder;

/**
 * Converts Protobuf messages to Java Objects and back.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@RequiredArgsConstructor
public class ProtoConverter {

  private static final MSG_Empty EMPTY = MSG_Empty.newBuilder().build();

  @NonNull
  public MSG_Notification createCatchupNotification() {
    return MSG_Notification.newBuilder().setType(MSG_Notification.Type.Catchup).build();
  }

  @NonNull
  public MSG_Notification createCompleteNotification() {
    return MSG_Notification.newBuilder().setType(MSG_Notification.Type.Complete).build();
  }

  @NonNull
  public MSG_Notification createNotificationFor(@NonNull Fact t) {
    MSG_Notification.Builder builder =
        MSG_Notification.newBuilder().setType(MSG_Notification.Type.Fact);
    builder.setFact(toProto(t));
    return builder.build();
  }

  public MSG_Notification createNotificationFor(List<Fact> stagedFacts) {
    MSG_Notification.Builder builder =
        MSG_Notification.newBuilder().setType(MSG_Notification.Type.Facts);
    builder.setFacts(toProto(stagedFacts));
    return builder.build();
  }

  @NonNull
  public MSG_Notification createNotificationForFactId(@NonNull UUID id) {
    MSG_Notification.Builder builder =
        MSG_Notification.newBuilder().setType(MSG_Notification.Type.Id);
    builder.setId(toProto(id));
    return builder.build();
  }

  @NonNull
  public MSG_Notification createNotificationForFastForward(@NonNull UUID id) {
    MSG_Notification.Builder builder =
        MSG_Notification.newBuilder().setType(MSG_Notification.Type.Ffwd);
    builder.setId(toProto(id));
    return builder.build();
  }

  @NonNull
  public MSG_UUID toProto(@NonNull UUID id) {
    return MSG_UUID
        .newBuilder()
        .setLsb(id.getLeastSignificantBits())
        .setMsb(id.getMostSignificantBits())
        .build();
  }

  @NonNull
  public MSG_UUID_AND_VERSION toProto(@NonNull UUID id, int version) {
    return MSG_UUID_AND_VERSION
        .newBuilder()
        .setLsb(id.getLeastSignificantBits())
        .setMsb(id.getMostSignificantBits())
        .setVer(version)
        .build();
  }

  public SubscriptionRequestTO fromProto(@NonNull MSG_SubscriptionRequest request) {
    return FactCastJson.readValue(SubscriptionRequestTO.class, request.getJson());
  }

  public MSG_SubscriptionRequest toProto(SubscriptionRequestTO request) {
    return MSG_SubscriptionRequest.newBuilder()
        .setJson(FactCastJson.writeValueAsString(request))
        .build();
  }

  public UUID fromProto(MSG_UUID request) {
    long lsb = request.getLsb();
    long msb = request.getMsb();
    return new UUID(msb, lsb);
  }

  @NonNull
  public IdAndVersion fromProto(MSG_UUID_AND_VERSION request) {
    long lsb = request.getLsb();
    long msb = request.getMsb();
    int version = request.getVer();
    return new IdAndVersion(new UUID(msb, lsb), version);
  }

  public Fact fromProto(MSG_Fact protoFact) {
    return Fact.of(protoFact.getHeader(), protoFact.getPayload());
  }

  @NonNull
  public MSG_Fact toProto(@NonNull Fact factMark) {
    MSG_Fact.Builder proto = MSG_Fact.newBuilder();
    proto.setHeader(factMark.jsonHeader());
    proto.setPayload(factMark.jsonPayload());
    return proto.build();
  }

  @NonNull
  public MSG_OptionalFact toProto(@NonNull Optional<Fact> optFact) {
    Builder proto = MSG_OptionalFact.newBuilder();
    boolean present = optFact.isPresent();
    proto.setPresent(present);
    if (present) {
      proto.setFact(toProto(optFact.get()));
    }
    return proto.build();
  }

  @NonNull
  public Optional<Fact> fromProto(@NonNull MSG_OptionalFact message) {
    if (!message.getPresent()) {
      return Optional.empty();
    } else {
      return Optional.of(fromProto(message.getFact()));
    }
  }

  @NonNull
  public OptionalLong fromProto(@NonNull MSG_OptionalSerial serialOf) {
    if (serialOf.getPresent()) {
      return OptionalLong.of(serialOf.getSerial());
    } else {
      return OptionalLong.empty();
    }
  }

  @NonNull
  public ProtocolVersion fromProto(@NonNull MSG_ServerProtocolVersion msg) {
    return ProtocolVersion.of(msg.getMajor(), msg.getMinor(), msg.getPatch());
  }

  @NonNull
  public Map<String, String> fromProto(@NonNull MSG_ServerProperties msg) {
    return msg.getPropertyMap();
  }

  @NonNull
  public ServerConfig fromProto(@NonNull MSG_ServerConfig msg) {
    return ServerConfig.of(fromProto(msg.getVersion()), fromProto(msg.getProperties()));
  }

  @NonNull
  public MSG_ServerProtocolVersion toProto(@NonNull ProtocolVersion v) {
    return MSG_ServerProtocolVersion.newBuilder()
        .setMajor(v.major())
        .setMinor(v.minor())
        .setPatch(v.patch())
        .build();
  }

  @NonNull
  public MSG_ServerConfig toProto(@NonNull ServerConfig cfg) {
    return MSG_ServerConfig.newBuilder()
        .setVersion(toProto(cfg.version()))
        .setProperties(toProto(cfg.properties()))
        .build();
  }

  @NonNull
  public MSG_ServerProperties toProto(@NonNull Map<String, String> property) {
    return MSG_ServerProperties.newBuilder().putAllProperty(property).build();
  }

  @NonNull
  public MSG_Empty empty() {
    return EMPTY;
  }

  @NonNull
  public MSG_OptionalSerial toProto(OptionalLong serialOf) {
    if (serialOf.isPresent()) {
      return MSG_OptionalSerial.newBuilder()
          .setPresent(true)
          .setSerial(serialOf.getAsLong())
          .build();
    } else {
      return MSG_OptionalSerial.newBuilder().setPresent(false).build();
    }
  }

  @NonNull
  public Set<String> fromProto(@NonNull MSG_StringSet set) {
    ProtocolStringList sList = set.getEmbeddedStringList();
    return new HashSet<>(sList);
  }

  @NonNull
  public MSG_StringSet toProto(@NonNull Set<String> set) {
    return MSG_StringSet.newBuilder().addAllEmbeddedString(set).build();
  }

  @NonNull
  public MSG_String toProto(@NonNull String ns) {
    return MSG_String.newBuilder().setEmbeddedString(ns).build();
  }

  @NonNull
  public String fromProto(@NonNull MSG_String request) {
    return request.getEmbeddedString();
  }

  public @NonNull MSG_Facts toProto(@NonNull List<? extends Fact> toPublish) {
    MSG_Facts.Builder ret = MSG_Facts.newBuilder();
    for (Fact fact : toPublish) {
      ret.addFact(toProto(fact));
    }
    return ret.build();
  }

  @NonNull
  public StateForRequest fromProto(@NonNull MSG_StateForRequest request) {
    List<UUID> aggIds =
        request.getAggIdsList().stream().map(this::fromProto).collect(Collectors.toList());
    String ns = request.getNsPresent() ? request.getNs() : null;
    return new StateForRequest(aggIds, ns);
  }

  @NonNull
  public ConditionalPublishRequest fromProto(@NonNull MSG_ConditionalPublishRequest request) {
    UUID token = null;
    if (request.getTokenPresent()) {
      token = fromProto(request.getToken());
    }

    return new ConditionalPublishRequest(fromProto(request.getFacts()), token);
  }

  public @NonNull List<Fact> fromProto(@NonNull MSG_Facts facts) {
    return facts.getFactList().stream().map(this::fromProto).collect(Collectors.toList());
  }

  @NonNull
  public MSG_ConditionalPublishResult toProto(boolean result) {
    return MSG_ConditionalPublishResult.newBuilder().setSuccess(result).build();
  }

  @NonNull
  public MSG_StateForRequest toProto(@NonNull StateForRequest req) {
    String ns = req.ns();
    MSG_StateForRequest.Builder b =
        MSG_StateForRequest.newBuilder()
            .setNsPresent(ns != null)
            .addAllAggIds(req.aggIds().stream().map(this::toProto).collect(Collectors.toList()));

    if (ns != null) {
      b.setNs(ns);
    }
    return b.build();
  }

  @NonNull
  public MSG_ConditionalPublishRequest toProto(@NonNull ConditionalPublishRequest req) {
    MSG_ConditionalPublishRequest.Builder b = MSG_ConditionalPublishRequest.newBuilder();
    b.setFacts(toProto(req.facts()));
    Optional<StateToken> token = req.token();
    boolean present = token.isPresent();
    b.setTokenPresent(present);
    if (present) {
      b.setToken(toProto(token.get().uuid()));
    }

    return b.build();
  }

  @NonNull
  public long fromProto(@NonNull MSG_CurrentDatabaseTime resp) {
    return resp.getMillis();
  }

  @NonNull
  public MSG_CurrentDatabaseTime toProto(long currentTime) {
    return MSG_CurrentDatabaseTime.newBuilder().setMillis(currentTime).build();
  }

  public MSG_SnapshotId toProto(SnapshotId id) {
    return MSG_SnapshotId.newBuilder().setKey(id.key()).setUuid(toProtoOptional(id.uuid())).build();
  }

  public MSG_OptionalUuid toProtoOptional(UUID uuid) {
    MSG_OptionalUuid.Builder builder = MSG_OptionalUuid.newBuilder();
    if (uuid == null) {
      return builder.setPresent(false).build();
    } else {
      return builder.setPresent(true).setUuid(toProto(uuid)).build();
    }
  }

  public Optional<Snapshot> fromProto(@NonNull MSG_OptionalSnapshot message) {
    if (!message.getPresent()) {
      return Optional.empty();
    } else {
      return Optional.of(fromProto(message.getSnapshot()));
    }
  }

  public Snapshot fromProto(@NonNull MSG_Snapshot snapshot) {
    return new Snapshot(
        fromProto(snapshot.getId()),
        fromProto(snapshot.getFactId()),
        fromProto(snapshot.getData()),
        snapshot.getCompressed());
  }

  public SnapshotId fromProto(@NonNull MSG_SnapshotId id) {
    return SnapshotId.of(id.getKey(), fromProto(id.getUuid()));
  }

  public UUID fromProto(@NonNull MSG_OptionalUuid uuid) {
    if (uuid.getPresent()) {
      return fromProto(uuid.getUuid());
    } else {
      return null;
    }
  }

  public byte[] fromProto(@NonNull ByteString data) {
    return data.toByteArray();
  }

  public MSG_Snapshot toProto(
      @NonNull SnapshotId id, @NonNull UUID state, @NonNull byte[] bytes, boolean compressed) {
    MSG_Snapshot.Builder ret =
        MSG_Snapshot.newBuilder()
            .setId(toProto(id))
            .setFactId(toProto(state))
            .setData(ByteString.copyFrom(bytes))
            .setCompressed(compressed);
    return ret.build();
  }

  public MSG_Snapshot toProto(Snapshot snap) {
    return toProto(snap.id(), snap.lastFact(), snap.bytes(), snap.compressed());
  }

  public MSG_OptionalSnapshot toProtoSnapshot(Optional<Snapshot> snapshot) {
    MSG_OptionalSnapshot.Builder ret = MSG_OptionalSnapshot.newBuilder();
    if (snapshot.isPresent()) {
      ret.setPresent(true);
      Snapshot snap = snapshot.get();
      ret.setSnapshot(toProto(snap.id(), snap.lastFact(), snap.bytes(), snap.compressed()));
    } else {
      ret.setPresent(false);
    }
    return ret.build();
  }

  public List<FactSpec> fromProto(MSG_FactSpecsJson request) {
    return FactCastJson.readValue(new TypeReference<List<FactSpec>>() {}, request.getJson());
  }

  public MSG_FactSpecsJson toProtoFactSpecs(List<FactSpec> specs) {
    return MSG_FactSpecsJson.newBuilder().setJson(FactCastJson.writeValueAsString(specs)).build();
  }

  public MSG_Notification createKeepaliveNotification() {
    return MSG_Notification.newBuilder().setType(Type.KeepAlive).build();
  }

  public FactStreamInfo fromProto(MSG_Info info) {
    return new FactStreamInfo(info.getSerialStart(), info.getSerialHorizon());
  }

  @NonNull
  public MSG_Info toProto(@NonNull FactStreamInfo info) {
    return MSG_Info.newBuilder()
        .setSerialStart(info.startSerial())
        .setSerialHorizon(info.horizonSerial())
        .build();
  }

  public MSG_Notification createInfoNotification(FactStreamInfo info) {
    return MSG_Notification.newBuilder().setType(Type.Info).setInfo(toProto(info)).build();
  }
}
