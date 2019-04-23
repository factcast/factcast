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
package org.factcast.grpc.api.conv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.StateForRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ConditionalPublishRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Empty;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalSerial;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ServerProperties;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ServerProtocolVersion;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_StateForRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Sets;

import lombok.NonNull;

public class ProtoConverterTest {

    ProtoConverter uut = new ProtoConverter();

    @Test
    void testToProtoUUID() {
        UUID probe = UUID.randomUUID();
        UUID copy = uut.fromProto(uut.toProto(probe));
        assertEquals(probe, copy);
    }

    @Test
    void testToProtoUUIDNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.toProto((UUID) null);
        });
    }

    @Test
    void testToProtoFactNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.toProto((Fact) null);
        });
    }

    @Test
    void testFromProtoOptionalNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.fromProto((MSG_OptionalFact) null);
        });
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
        Fact probe = Fact.builder()
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
        Fact probe = Fact.builder()
                .ns(ns)
                .aggId(aggId)
                .meta(key1, value1)
                .meta(key2, value2)
                .type(type)
                .build(payload);
        Fact probe2 = Fact.builder().ns(ns + "foo").aggId(UUID.randomUUID()).type("narf").build(
                payload);

        MSG_Facts proto = uut.toProto(Arrays.asList(probe, probe2));

        assertFactEqualTo(probe, uut.fromProto(proto.getFact(0)));
        assertFactEqualTo(probe2, uut.fromProto(proto.getFact(1)));
    }

    @Test
    void testToProtoFacts_empty() {
        MSG_Facts proto = uut.toProto(new ArrayList<Fact>());

        assertTrue(proto.getFactList().isEmpty());
    }

    @Test
    void testToProtoFacts_nullArg() {
        assertThrows(NullPointerException.class, () -> {
            uut.toProto((List<Fact>) null);
        });
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
    void testCreateCompleteNotification() {
        MSG_Notification n = uut.createCompleteNotification();
        assertNotNull(n);
        assertEquals(MSG_Notification.Type.Complete, n.getType());
    }

    @Test
    void testCreateNotificationForFactNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.createNotificationFor((Fact) null);
        });
    }

    @Test
    void testCreateNotificationForIdNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.createNotificationFor((UUID) null);
        });
    }

    @Test
    void testCreateNotificationForFact() {
        final TestFact probe = new TestFact().ns("123");
        MSG_Notification n = uut.createNotificationFor(probe);
        assertNotNull(n);
        assertEquals(MSG_Notification.Type.Fact, n.getType());
        assertEquals(probe.ns(), uut.fromProto(n.getFact()).ns());
    }

    @Test
    void testCreateNotificationForUUID() {
        final UUID probe = UUID.randomUUID();
        MSG_Notification n = uut.createNotificationFor(probe);
        assertNotNull(n);
        assertEquals(MSG_Notification.Type.Id, n.getType());
        assertEquals(probe, uut.fromProto(n.getId()));
    }

    @Test
    void testFromProtoMSG_SubscriptionRequestNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.fromProto((MSG_SubscriptionRequest) null);
        });
    }

    @Test
    void testToProtoSubscriptionRequestNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.toProto((SubscriptionRequestTO) null);
        });
    }

    @Test
    void testToProtoSubscriptionRequest() {
        SubscriptionRequestTO to = new SubscriptionRequestTO().continuous(true)
                .ephemeral(false)
                .debugInfo("test")
                .maxBatchDelayInMs(13)
                ;
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
    public void testFromProtoMSG_OptionalSerial() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            uut.fromProto((MSG_OptionalSerial) null);
        });

        assertFalse(uut.fromProto(MSG_OptionalSerial.newBuilder()
                .setPresent(false)
                .setSerial(4)
                .build()).isPresent());

        assertFalse(uut.fromProto(MSG_OptionalSerial.newBuilder().setPresent(false).build())
                .isPresent());
        @NonNull
        OptionalLong optSerial = uut.fromProto(MSG_OptionalSerial.newBuilder()
                .setPresent(true)
                .setSerial(4)
                .build());
        assertTrue(optSerial.isPresent());
        assertEquals(4, optSerial.getAsLong());

    }

    @Test
    public void testFromProtoMSG_ServerProtocolVersion() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            uut.fromProto((MSG_ServerProtocolVersion) null);
        });

        assertEquals(ProtocolVersion.of(1, 2, 3),
                uut.fromProto(MSG_ServerProtocolVersion.newBuilder()
                        .setMajor(1)
                        .setMinor(2)
                        .setPatch(3)
                        .build()));

    }

    @Test
    public void testFromProtoMSG_ServerProperties() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            uut.fromProto((MSG_ServerProperties) null);
        });

        assertEquals(Maps.newHashMap("foo", "bar"),
                uut.fromProto(MSG_ServerProperties.newBuilder().putProperty("foo", "bar").build()));
    }

    @Test
    public void testFromProtoMSG_ServerConfig() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            uut.fromProto((MSG_ServerConfig) null);
        });

        MSG_ServerProperties props = MSG_ServerProperties.newBuilder()
                .putProperty("foo", "bar")
                .build();
        ProtocolVersion v = ProtocolVersion.of(1, 2, 3);
        MSG_ServerProtocolVersion version = uut.toProto(v);

        ServerConfig config = uut
                .fromProto(MSG_ServerConfig.newBuilder()
                        .setProperties(props)
                        .setVersion(version)
                        .build());

        assertEquals(v, config.version());
        assertEquals(Maps.newHashMap("foo", "bar"), config.properties());

    }

    @Test
    public void testToProtoProtocolVersion() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            uut.toProto((ProtocolVersion) null);
        });

        ProtocolVersion v1 = ProtocolVersion.of(1, 2, 3);
        ProtocolVersion v2 = uut.fromProto(uut.toProto(v1));

        assertNotSame(v1, v2);
        assertEquals(v1, v2);

    }

    @Test
    public void testToProtoServerConfig() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            uut.toProto((ServerConfig) null);
        });

        ProtocolVersion v = ProtocolVersion.of(1, 2, 3);
        ServerConfig cfg1 = ServerConfig.of(v, Maps.newHashMap("foo", "bar"));
        ServerConfig cfg2 = uut.fromProto(uut.toProto(cfg1));

        assertNotSame(cfg1, cfg2);
        assertEquals(cfg1, cfg2);

    }

    @Test
    public void testToProtoServerProperties() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            uut.toProto((HashMap<String, String>) null);
        });

        Map<String, String> map1 = Maps.newHashMap("poit", "narf");
        Map<String, String> map2 = uut.fromProto(uut.toProto(map1));

        assertNotSame(map1, map2);
        assertEquals(map1, map2);

    }

    @Test
    public void testFromProtoMSG_StringSet() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            uut.toProto((Set<String>) null);
        });

        HashSet<String> set1 = Sets.newHashSet("foo", "bar");
        Set<String> set2 = uut.fromProto(uut.toProto(set1));

        assertNotSame(set1, set2);
        assertEquals(set1, set2);

    }

    @Test
    public void testToProtoString() throws Exception {

        assertThrows(NullPointerException.class, () -> {
            uut.toProto((String) null);
        });

        String s1 = UUID.randomUUID().toString();
        String s2 = uut.fromProto(uut.toProto(s1));

        assertSame(s1, s2);

    }

    @Test
    public void testFromProtoMSG_StateForRequest_nsSet() throws Exception {

        MSG_StateForRequest request = MSG_StateForRequest.newBuilder()
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
    public void testFromProtoMSG_StateForRequest_nsNotSet() throws Exception {

        MSG_StateForRequest request = MSG_StateForRequest.newBuilder()
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
    public void testFromProtoMSG_StateForRequest_emptyAggIds() throws Exception {

        MSG_StateForRequest request = MSG_StateForRequest.newBuilder().setNsPresent(false).build();
        StateForRequest req = uut.fromProto(request);
        assertNotNull(req.aggIds());
        assertEquals(0, req.aggIds().size());

    }

    @Test
    public void testToProtoBoolean() throws Exception {
        assertFalse(uut.toProto(false).getSuccess());
        assertTrue(uut.toProto(true).getSuccess());
    }

    @Test
    public void testFromProtoMSG_ConditionalPublishRequest() throws Exception {

        assertThrows(NullPointerException.class, () -> {
            uut.fromProto((MSG_ConditionalPublishRequest) null);
        });

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
        req = MSG_ConditionalPublishRequest.newBuilder()
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
    public void testFromProtoMSG_Facts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            uut.fromProto((MSG_Facts) null);
        });
    }

    @Test
    public void testToProtoStateForRequest() throws Exception {

        assertThrows(NullPointerException.class, () -> {
            uut.toProto((StateForRequest) null);
        });

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
    public void testToProtoConditionalPublishRequest() throws Exception {

        assertThrows(NullPointerException.class, () -> {
            uut.toProto((ConditionalPublishRequest) null);
        });

        {
            UUID id = UUID.randomUUID();
            Fact f2 = new TestFact();
            Fact f1 = new TestFact();
            ConditionalPublishRequest req = new ConditionalPublishRequest(Lists.newArrayList(f1,
                    f2), id);

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
            ConditionalPublishRequest req = new ConditionalPublishRequest(Lists.newArrayList(f1,
                    f2), null);

            MSG_ConditionalPublishRequest msg = uut.toProto(req);
            assertThat(msg.getTokenPresent()).isFalse();
            assertThat(msg.getFacts().getFactList().get(0)).isEqualTo(uut.toProto(f1));
            assertThat(msg.getFacts().getFactList().get(1)).isEqualTo(uut.toProto(f2));
            assertThat(msg.getFacts().getFactList().size()).isEqualTo(2);
        }

    }
}
