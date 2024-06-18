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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import java.time.LocalDate;
import java.util.*;
import lombok.NonNull;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.TestFactStreamPosition;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.StateForRequest;
import org.factcast.grpc.api.gen.FactStoreProto;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification.Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ProtoConverterTest {

  final ProtoConverter uut = new ProtoConverter();

  @Test
  void testToProtoUUID() {
    UUID probe = UUID.randomUUID();
    UUID copy = uut.fromProto(uut.toProto(probe));
    assertEquals(probe, copy);
  }

  @Test
  void testToProtoUUIDNull() {
    Assertions.assertThrows(NullPointerException.class, () -> uut.toProto((UUID) null));
  }

  @Test
  void testToProtoFactNull() {
    Assertions.assertThrows(NullPointerException.class, () -> uut.toProto((Fact) null));
  }

  @Test
  void testFromProtoOptionalNull() {
    Assertions.assertThrows(
        NullPointerException.class, () -> uut.fromProto((MSG_OptionalFact) null));
  }

  @Test
  void testToProtoFact() {
    UUID aggId = UUID.randomUUID();
    String payload = "{\"foo\":\"bar\"}";
    String value1 = "1";
    String key1 = "k1";
    String value2 = "2";
    String key2 = "k2";
    String type = "da-type";
    String ns = "da-ns";
    Fact probe =
        Fact.builder()
            .ns(ns)
            .aggId(aggId)
            .meta(key1, value1)
            .meta(key2, value2)
            .type(type)
            .build(payload);
    Fact copy = uut.fromProto(uut.toProto(probe));
    assertEquals(probe.id(), copy.id());
    assertEquals(probe.aggIds(), copy.aggIds());
    assertEquals(probe.type(), copy.type());
    assertEquals(probe.ns(), copy.ns());
    assertEquals(probe.meta(key1), copy.meta(key1));
    assertEquals(probe.meta(key2), copy.meta(key2));
    assertEquals(probe.jsonPayload(), copy.jsonPayload());
    assertEquals(probe.jsonHeader(), copy.jsonHeader());
  }

  @Test
  void testToProtoFacts() {
    UUID aggId = UUID.randomUUID();
    String payload = "{\"foo\":\"bar\"}";
    String value1 = "1";
    String key1 = "k1";
    String value2 = "2";
    String key2 = "k2";
    String type = "da-type";
    String ns = "da-ns";
    Fact probe =
        Fact.builder()
            .ns(ns)
            .aggId(aggId)
            .meta(key1, value1)
            .meta(key2, value2)
            .type(type)
            .build(payload);
    Fact probe2 =
        Fact.builder().ns(ns + "foo").aggId(UUID.randomUUID()).type("narf").build(payload);

    MSG_Facts proto = uut.toProto(Arrays.asList(probe, probe2));

    assertFactEqualTo(probe, uut.fromProto(proto.getFact(0)));
    assertFactEqualTo(probe2, uut.fromProto(proto.getFact(1)));
  }

  @Test
  void testToProtoFacts_empty() {
    MSG_Facts proto = uut.toProto(new ArrayList<>());

    assertTrue(proto.getFactList().isEmpty());
  }

  @Test
  void testToProtoFacts_nullArg() {
    assertThrows(NullPointerException.class, () -> uut.toProto((List<Fact>) null));
  }

  private void assertFactEqualTo(Fact probe, Fact copy) {
    assertEquals(probe.id(), copy.id());
    assertEquals(probe.aggIds(), copy.aggIds());
    assertEquals(probe.type(), copy.type());
    assertEquals(probe.ns(), copy.ns());
    assertEquals(probe.jsonPayload(), copy.jsonPayload());
    assertEquals(probe.jsonHeader(), copy.jsonHeader());
  }

  @Test
  void testToOptionalProtoFactEmpty() {
    Optional<Fact> probe = Optional.empty();
    Optional<Fact> copy = uut.fromProto(uut.toProto(probe));
    assertFalse(copy.isPresent());
  }

  @Test
  void testToOptionalProtoFact() {
    Optional<Fact> probe = Optional.of(new TestFact().ns("oink"));
    Optional<Fact> copy = uut.fromProto(uut.toProto(probe));
    assertTrue(copy.isPresent());
    assertEquals(probe.get().ns(), copy.get().ns());
  }

  @Test
  void testCreateCatchupNotification() {
    MSG_Notification n = uut.createCatchupNotification();
    assertNotNull(n);
    assertEquals(MSG_Notification.Type.Catchup, n.getType());
  }

  @Test
  void testCreateKeepaliveNotification() {
    MSG_Notification n = uut.createKeepaliveNotification();
    assertNotNull(n);
    assertEquals(Type.KeepAlive, n.getType());
  }

  @Test
  void testCreateCompleteNotification() {
    MSG_Notification n = uut.createCompleteNotification();
    assertNotNull(n);
    assertEquals(MSG_Notification.Type.Complete, n.getType());
  }

  @Test
  void testCreateNotificationForFactNull() {
    Assertions.assertThrows(
        NullPointerException.class, () -> uut.createNotificationFor((Fact) null));
  }

  @Test
  void testCreateNotificationForIdNull() {
    Assertions.assertThrows(
        NullPointerException.class, () -> uut.createNotificationForFactId((UUID) null));
  }

  @Test
  void testCreateNotificationForFact() {
    TestFact probe = new TestFact().ns("123");
    MSG_Notification n = uut.createNotificationFor(probe);
    assertNotNull(n);
    assertEquals(MSG_Notification.Type.Fact, n.getType());
    assertEquals(probe.ns(), uut.fromProto(n.getFact()).ns());
  }

  @Test
  void testCreateNotificationForList() {
    TestFact probe1 = new TestFact().ns("123");
    TestFact probe2 = new TestFact().ns("456");
    MSG_Notification n = uut.createNotificationFor(Lists.newArrayList(probe1, probe2));
    assertNotNull(n);
    assertEquals(Type.Facts, n.getType());
    assertEquals(probe1.ns(), uut.fromProto(n.getFacts().getFact(0)).ns());
    assertEquals(probe2.ns(), uut.fromProto(n.getFacts().getFact(1)).ns());
  }

  @Test
  void testCreateNotificationForUUID() {
    UUID probe = UUID.randomUUID();
    MSG_Notification n = uut.createNotificationForFactId(probe);
    assertNotNull(n);
    assertEquals(MSG_Notification.Type.Id, n.getType());
    assertEquals(probe, uut.fromProto(n.getId()));
  }

  @Test
  void testCreateNotificationForFastForward() {
    FactStreamPosition probe = TestFactStreamPosition.random();
    MSG_Notification n = uut.toProto(probe);
    assertNotNull(n);
    assertEquals(Type.Ffwd, n.getType());
    assertEquals(probe, uut.fromProto(n.getId(), n.getSerial()));
  }

  @Test
  void testFromProtoMSG_SubscriptionRequestNull() {
    Assertions.assertThrows(
        NullPointerException.class, () -> uut.fromProto((MSG_SubscriptionRequest) null));
  }

  @Test
  void testToProtoSubscriptionRequestNull() {
    Assertions.assertThrows(
        NullPointerException.class, () -> uut.toProto((SubscriptionRequestTO) null));
  }

  @Test
  void testToProtoSubscriptionRequest() {
    SubscriptionRequestTO to =
        new SubscriptionRequestTO()
            .continuous(true)
            .ephemeral(false)
            .debugInfo("test")
            .maxBatchDelayInMs(13);
    to.addSpecs(Collections.singletonList(FactSpec.ns("foo")));
    SubscriptionRequestTO copy = uut.fromProto(uut.toProto(to));
    assertEquals(to.debugInfo(), copy.debugInfo());
    assertEquals(to.ephemeral(), copy.ephemeral());
    assertEquals(to.continuous(), copy.continuous());
    assertEquals(to.maxBatchDelayInMs(), copy.maxBatchDelayInMs());
    assertEquals(to.specs().get(0).type(), copy.specs().get(0).type());
    assertEquals(to.specs().get(0).ns(), copy.specs().get(0).ns());
  }

  @Test
  void testToProtoOptionalLongPresent() {
    MSG_OptionalSerial msg = uut.toProto(OptionalLong.of(133));
    assertTrue(msg.getPresent());
    assertEquals(133, msg.getSerial());
  }

  @Test
  void testToProtoOptionalLongEmpty() {
    MSG_OptionalSerial msg = uut.toProto(OptionalLong.empty());
    assertFalse(msg.getPresent());
  }

  @Test
  void testEmpty() {
    assertEquals(MSG_Empty.newBuilder().build(), uut.empty());
  }

  @Test
  public void testFromProtoMSG_OptionalSerial() {
    assertThrows(NullPointerException.class, () -> uut.fromProto((MSG_OptionalSerial) null));

    assertFalse(
        uut.fromProto(MSG_OptionalSerial.newBuilder().setPresent(false).setSerial(4).build())
            .isPresent());

    assertFalse(
        uut.fromProto(MSG_OptionalSerial.newBuilder().setPresent(false).build()).isPresent());
    @NonNull
    OptionalLong optSerial =
        uut.fromProto(MSG_OptionalSerial.newBuilder().setPresent(true).setSerial(4).build());
    assertTrue(optSerial.isPresent());
    assertEquals(4, optSerial.getAsLong());
  }

  @Test
  public void testFromProtoMSG_ServerProtocolVersion() {
    assertThrows(NullPointerException.class, () -> uut.fromProto((MSG_ServerProtocolVersion) null));

    assertEquals(
        ProtocolVersion.of(1, 2, 3),
        uut.fromProto(
            MSG_ServerProtocolVersion.newBuilder().setMajor(1).setMinor(2).setPatch(3).build()));
  }

  @Test
  public void testFromProtoMSG_ServerProperties() {
    assertThrows(NullPointerException.class, () -> uut.fromProto((MSG_ServerProperties) null));

    assertEquals(
        Maps.newHashMap("foo", "bar"),
        uut.fromProto(MSG_ServerProperties.newBuilder().putProperty("foo", "bar").build()));
  }

  @Test
  public void testFromProtoMSG_ServerConfig() {
    assertThrows(NullPointerException.class, () -> uut.fromProto((MSG_ServerConfig) null));

    MSG_ServerProperties props =
        MSG_ServerProperties.newBuilder().putProperty("foo", "bar").build();
    ProtocolVersion v = ProtocolVersion.of(1, 2, 3);
    MSG_ServerProtocolVersion version = uut.toProto(v);

    ServerConfig config =
        uut.fromProto(
            MSG_ServerConfig.newBuilder().setProperties(props).setVersion(version).build());

    assertEquals(v, config.version());
    assertEquals(Maps.newHashMap("foo", "bar"), config.properties());
  }

  @Test
  public void testToProtoProtocolVersion() {
    assertThrows(NullPointerException.class, () -> uut.toProto((ProtocolVersion) null));

    ProtocolVersion v1 = ProtocolVersion.of(1, 2, 3);
    ProtocolVersion v2 = uut.fromProto(uut.toProto(v1));

    assertNotSame(v1, v2);
    assertEquals(v1, v2);
  }

  @Test
  public void testToProtoServerConfig() {
    assertThrows(NullPointerException.class, () -> uut.toProto((ServerConfig) null));

    ProtocolVersion v = ProtocolVersion.of(1, 2, 3);
    ServerConfig cfg1 = ServerConfig.of(v, Maps.newHashMap("foo", "bar"));
    ServerConfig cfg2 = uut.fromProto(uut.toProto(cfg1));

    assertNotSame(cfg1, cfg2);
    assertEquals(cfg1, cfg2);
  }

  @Test
  public void testToProtoServerProperties() {
    assertThrows(NullPointerException.class, () -> uut.toProto((HashMap<String, String>) null));

    Map<String, String> map1 = Maps.newHashMap("poit", "narf");
    Map<String, String> map2 = uut.fromProto(uut.toProto(map1));

    assertNotSame(map1, map2);
    assertEquals(map1, map2);
  }

  @Test
  public void testFromProtoMSG_StringSet() {
    assertThrows(NullPointerException.class, () -> uut.toProto((Set<String>) null));

    HashSet<String> set1 = Sets.newHashSet("foo", "bar");
    Set<String> set2 = uut.fromProto(uut.toProto(set1));

    assertNotSame(set1, set2);
    assertEquals(set1, set2);
  }

  @Test
  public void testToProtoString() {

    assertThrows(NullPointerException.class, () -> uut.toProto((String) null));

    String s1 = UUID.randomUUID().toString();
    String s2 = uut.fromProto(uut.toProto(s1));

    assertSame(s1, s2);
  }

  @Test
  public void testFromProtoMSG_StateForRequest_nsSet() {

    MSG_StateForRequest request =
        MSG_StateForRequest.newBuilder()
            .setNs("foo")
            .setNsPresent(true)
            .addAggIds(MSG_UUID.newBuilder().setLsb(1).setMsb(2).build())
            .build();
    StateForRequest req = uut.fromProto(request);
    assertEquals("foo", req.ns());
    assertNotNull(req.aggIds());
    assertEquals(1, req.aggIds().size());
    assertEquals(new UUID(2, 1), req.aggIds().get(0));
  }

  @Test
  public void testFromProtoMSG_StateForRequest_nsNotSet() {

    MSG_StateForRequest request =
        MSG_StateForRequest.newBuilder()
            .setNs("foo")
            .setNsPresent(false)
            .addAggIds(MSG_UUID.newBuilder().setLsb(1).setMsb(2).build())
            .build();
    StateForRequest req = uut.fromProto(request);
    assertThat(req.ns()).isNull();
    assertNotNull(req.aggIds());
    assertEquals(1, req.aggIds().size());
    assertEquals(new UUID(2, 1), req.aggIds().get(0));
  }

  @Test
  public void testFromProtoMSG_StateForRequest_emptyAggIds() {

    MSG_StateForRequest request = MSG_StateForRequest.newBuilder().setNsPresent(false).build();
    StateForRequest req = uut.fromProto(request);
    assertNotNull(req.aggIds());
    assertEquals(0, req.aggIds().size());
  }

  @Test
  public void testToProtoBoolean() {
    assertFalse(uut.toProto(false).getSuccess());
    assertTrue(uut.toProto(true).getSuccess());
  }

  @Test
  public void testFromProtoMSG_ConditionalPublishRequest() {

    assertThrows(
        NullPointerException.class, () -> uut.fromProto((MSG_ConditionalPublishRequest) null));

    MSG_ConditionalPublishRequest req = MSG_ConditionalPublishRequest.newBuilder().build();
    ConditionalPublishRequest r = uut.fromProto(req);
    assertThat(r.facts()).isEmpty();
    assertThat(r.token()).isNotPresent();

    UUID uuid = new UUID(3, 4);
    MSG_UUID id = uut.toProto(uuid);
    req = MSG_ConditionalPublishRequest.newBuilder().setTokenPresent(true).setToken(id).build();
    r = uut.fromProto(req);
    assertThat(r.facts()).isEmpty();
    assertThat(r.token()).isPresent();
    assertThat(r.token().get().uuid()).isEqualTo(uuid);

    TestFact f1 = new TestFact();
    TestFact f2 = new TestFact();
    List<Fact> factList = Arrays.asList(f1, f2);
    MSG_Facts facts = uut.toProto(factList);
    req =
        MSG_ConditionalPublishRequest.newBuilder()
            .setTokenPresent(true)
            .setToken(id)
            .setFacts(facts)
            .build();
    r = uut.fromProto(req);
    assertThat(r.facts()).isNotEmpty();
    assertThat(r.token()).isPresent();
    assertThat(r.token().get().uuid()).isEqualTo(uuid);
    assertThat(r.facts().get(0).id()).isEqualTo(f1.id());
    assertThat(r.facts().get(1).id()).isEqualTo(f2.id());
  }

  @Test
  public void testFromProtoMSG_Facts() {
    assertThrows(NullPointerException.class, () -> uut.fromProto((MSG_Facts) null));
  }

  @Test
  public void testToProtoStateForRequest() {

    assertThrows(NullPointerException.class, () -> uut.toProto((StateForRequest) null));

    StateForRequest req = new StateForRequest(Lists.emptyList(), null);
    MSG_StateForRequest msg = uut.toProto(req);
    assertThat(msg.getNsPresent()).isFalse();
    assertThat(msg.getAggIdsList()).isEmpty();

    req = new StateForRequest(Lists.emptyList(), "foo");
    msg = uut.toProto(req);
    assertThat(msg.getNsPresent()).isTrue();
    assertThat(msg.getNs()).isEqualTo("foo");
    assertThat(msg.getAggIdsList()).isEmpty();

    UUID u1 = new UUID(0, 1);
    UUID u2 = new UUID(0, 2);
    List<UUID> ids = Arrays.asList(u1, u2);
    req = new StateForRequest(ids, "foo");
    msg = uut.toProto(req);
    assertThat(msg.getNsPresent()).isTrue();
    assertThat(msg.getNs()).isEqualTo("foo");
    assertThat(msg.getAggIdsList()).isNotEmpty();
    assertThat(msg.getAggIdsList().size()).isEqualTo(2);
    assertThat(msg.getAggIdsList().get(0)).isEqualTo(uut.toProto(u1));
    assertThat(msg.getAggIdsList().get(1)).isEqualTo(uut.toProto(u2));
  }

  @Test
  public void testToProtoConditionalPublishRequest() {

    assertThrows(NullPointerException.class, () -> uut.toProto((ConditionalPublishRequest) null));

    {
      UUID id = UUID.randomUUID();
      Fact f2 = new TestFact();
      Fact f1 = new TestFact();
      ConditionalPublishRequest req = new ConditionalPublishRequest(Lists.newArrayList(f1, f2), id);

      MSG_ConditionalPublishRequest msg = uut.toProto(req);
      assertThat(msg.getTokenPresent()).isTrue();
      assertThat(msg.getToken()).isEqualTo(uut.toProto(id));
      assertThat(msg.getFacts().getFactList().get(0)).isEqualTo(uut.toProto(f1));
      assertThat(msg.getFacts().getFactList().get(1)).isEqualTo(uut.toProto(f2));
      assertThat(msg.getFacts().getFactList().size()).isEqualTo(2);
    }

    {
      Fact f2 = new TestFact();
      Fact f1 = new TestFact();
      ConditionalPublishRequest req =
          new ConditionalPublishRequest(Lists.newArrayList(f1, f2), null);

      MSG_ConditionalPublishRequest msg = uut.toProto(req);
      assertThat(msg.getTokenPresent()).isFalse();
      assertThat(msg.getFacts().getFactList().get(0)).isEqualTo(uut.toProto(f1));
      assertThat(msg.getFacts().getFactList().get(1)).isEqualTo(uut.toProto(f2));
      assertThat(msg.getFacts().getFactList().size()).isEqualTo(2);
    }
  }

  @Test
  public void testFromProtoMSG_CurrentDatabaseTime() {
    long probe = 123L;

    assertEquals(probe, uut.fromProto(uut.toProto(probe)));
  }

  @Test
  public void testFromProtoMSG_CurrentDatabaseTimeNull() {
    assertThrows(
        NullPointerException.class,
        () -> {
          MSG_CurrentDatabaseTime t = null;
          uut.fromProto(t);
        });
  }

  @Test
  public void testFromProtoMSG_UUID_AND_VERSION() {
    MSG_UUID_AND_VERSION msg =
        MSG_UUID_AND_VERSION.newBuilder().setLsb(1).setMsb(2).setVer(99).build();
    @NonNull IdAndVersion actual = uut.fromProto(msg);
    assertNotNull(actual);
    assertThat(actual.uuid()).isEqualTo(new UUID(2, 1));
    assertThat(actual.version()).isEqualTo(99);
  }

  @Test
  public void testToProtoMSG_UUID_AND_VERSION() {
    @NonNull FactStoreProto.MSG_UUID_AND_VERSION actual = uut.toProto(new UUID(2, 1), 99);
    assertNotNull(actual);
    assertThat(actual.getLsb()).isEqualTo(1);
    assertThat(actual.getMsb()).isEqualTo(2);
    assertThat(actual.getVer()).isEqualTo(99);
  }

  @Test
  void toProtoSnapshotId() {
    SnapshotId snapId = SnapshotId.of("test234", UUID.randomUUID());
    MSG_SnapshotId msg_snapshotId = uut.toProto(snapId);

    assertThat(msg_snapshotId).isNotNull();
    assertThat(uut.fromProto(msg_snapshotId.getUuid())).isNotNull().isEqualTo(snapId.uuid());
    assertThat(msg_snapshotId.getKey()).isNotNull().isEqualTo(snapId.key());
  }

  @Test
  void toProtoOptionalLong() {
    OptionalLong l = OptionalLong.of(123);
    MSG_OptionalSerial msg_optionalSerial = uut.toProto(l);

    assertThat(msg_optionalSerial).isNotNull();
    assertThat(msg_optionalSerial.getSerial()).isEqualTo(123);
  }

  @Test
  void toProtoOptionalLongEmpty() {
    OptionalLong l = OptionalLong.empty();
    MSG_OptionalSerial msg_optionalSerial = uut.toProto(l);

    assertThat(msg_optionalSerial).isNotNull();
    assertThat(msg_optionalSerial.getPresent()).isFalse();
  }

  @Test
  void toProtoOptionalUUID() {

    UUID id = UUID.randomUUID();
    MSG_OptionalUuid msg_optionalUuid = uut.toProtoOptional(id);

    assertThat(msg_optionalUuid).isNotNull();
    assertThat(msg_optionalUuid.getPresent()).isTrue();
    assertThat(uut.fromProto(msg_optionalUuid.getUuid())).isEqualTo(id);
  }

  @Test
  void fromProtoMSG_OptionalSnapshotEmpty() {
    MSG_OptionalSnapshot os = MSG_OptionalSnapshot.newBuilder().setPresent(false).build();

    Optional<Snapshot> snapshot = uut.fromProto(os);
    assertThat(snapshot).isEmpty();
  }

  @Test
  void fromProtoMSG_OptionalSnapshot() {
    UUID factId = UUID.randomUUID();
    SnapshotId snapId = SnapshotId.of("test123", UUID.randomUUID());

    MSG_Snapshot snap = uut.toProto(snapId, factId, "huhu".getBytes(Charsets.UTF_8), false);

    MSG_OptionalSnapshot os =
        MSG_OptionalSnapshot.newBuilder().setPresent(true).setSnapshot(snap).build();

    Optional<Snapshot> snapshot = uut.fromProto(os);
    assertThat(snapshot).isPresent();
    assertThat(snapshot.get()).isNotNull();
    assertThat(snapshot.get().id()).isEqualTo(snapId);
    assertThat(snapshot.get().compressed()).isFalse();
    assertThat(snapshot.get().lastFact()).isEqualTo(factId);
    assertThat(new String(snapshot.get().bytes())).isEqualTo("huhu");
  }

  @Test
  void toProtoSnapshotOptionalEmpty() {
    Optional<Snapshot> snapshot = uut.fromProto(uut.toProtoSnapshot(Optional.empty()));
    assertThat(snapshot).isEmpty();
  }

  @Test
  void toProtoSnapshotOptional() {
    UUID factId = UUID.randomUUID();
    SnapshotId snapId = SnapshotId.of("test123", UUID.randomUUID());
    MSG_Snapshot snap = uut.toProto(snapId, factId, "huhu".getBytes(Charsets.UTF_8), false);
    MSG_OptionalSnapshot osnap =
        MSG_OptionalSnapshot.newBuilder().setPresent(true).setSnapshot(snap).build();

    Optional<Snapshot> snapshot = uut.fromProto(osnap);
    assertThat(snapshot).isPresent();
    assertThat(snapshot.get().lastFact()).isEqualTo(factId);
    assertThat(new String(snapshot.get().bytes())).isEqualTo("huhu");
    assertThat(snapshot.get().id()).isEqualTo(snapId);

    MSG_OptionalSnapshot msg_optionalSnapshot = uut.toProtoSnapshot(snapshot);
    assertThat(msg_optionalSnapshot.getPresent()).isTrue();
    assertThat(msg_optionalSnapshot.getSnapshot().getFactId()).isEqualTo(uut.toProto(factId));
    assertThat(msg_optionalSnapshot.getSnapshot().getData().toStringUtf8()).isEqualTo("huhu");
    assertThat(msg_optionalSnapshot.getSnapshot().getId()).isEqualTo(uut.toProto(snapId));
  }

  @Test
  void fromProtoMSG_FactSpecsJsonEmpty() {
    String json = "[]";
    MSG_FactSpecsJson m = MSG_FactSpecsJson.newBuilder().setJson(json).build();
    List<FactSpec> factSpecs = uut.fromProto(m);
    assertThat(factSpecs).isEmpty();
  }

  @Test
  void fromProtoMSG_FactSpecsJson() {
    FactSpec f1 = FactSpec.ns("foo").type("bar").version(1);
    FactSpec f2 = FactSpec.ns("x").type("y").version(2);
    ArrayList<FactSpec> l = Lists.newArrayList(f1, f2);
    MSG_FactSpecsJson m = uut.toProtoFactSpecs(l);
    List<FactSpec> factSpecs = uut.fromProto(m);
    assertThat(factSpecs).isNotEmpty().hasSize(2).contains(f1).contains(f2);
  }

  @Test
  void fromProtoMSG_OptionalUuidEmpty() {
    MSG_OptionalUuid msg = MSG_OptionalUuid.newBuilder().setPresent(false).build();
    assertThat(uut.fromProto(msg)).isNull();
  }

  @Test
  void toProtoOptionalUUIDNull() {
    MSG_OptionalUuid msg = uut.toProtoOptional(null);
    assertThat(msg.getPresent()).isFalse();
  }

  @Test
  void fromProtoInfo() {
    MSG_Info msg = MSG_Info.newBuilder().setSerialStart(2).setSerialHorizon(3).build();
    assertThat(uut.fromProto(msg).startSerial()).isEqualTo(2);
    assertThat(uut.fromProto(msg).horizonSerial()).isEqualTo(3);
  }

  @Test
  void createInfoNotification() {
    FactStreamInfo info = new FactStreamInfo(2, 3);
    MSG_Notification msg = uut.createInfoNotification(info);

    assertThat(msg.getType()).isEqualTo(Type.Info);
    assertThat(msg.getInfo().getSerialStart()).isEqualTo(2);
    assertThat(msg.getInfo().getSerialHorizon()).isEqualTo(3);
  }

  @Test
  void toProtoDate() {
    LocalDate ld = LocalDate.of(2023, 12, 7);
    assertThat(uut.fromProto(uut.toProto(ld))).isEqualTo(ld);
  }

  @Test
  void toProtoSnap() {
    SnapshotId id = SnapshotId.of("narf", UUID.randomUUID());
    assertThat(uut.fromProto(uut.toProto(id))).isEqualTo(id);
  }

  @Test
  void toProtoCurrentDatabaseTimestamp() {
    long ts = 42;
    assertThat(uut.fromProto(uut.toProtoTime(ts))).isEqualTo(ts);
  }
}
