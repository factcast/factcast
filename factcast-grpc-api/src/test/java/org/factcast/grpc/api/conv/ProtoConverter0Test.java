package org.factcast.grpc.api.conv;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Empty;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalSerial;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ProtoConverter0Test {

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
                .ns(ns)
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
                .marks(true);
        to.addSpecs(Collections.singletonList(FactSpec.ns("foo")));
        SubscriptionRequestTO copy = uut.fromProto(uut.toProto(to));
        assertEquals(to.debugInfo(), copy.debugInfo());
        assertEquals(to.ephemeral(), copy.ephemeral());
        assertEquals(to.continuous(), copy.continuous());
        assertEquals(to.maxBatchDelayInMs(), copy.maxBatchDelayInMs());
        assertEquals(to.specs().get(0).type(), copy.specs().get(0).type());
        assertEquals(to.specs().get(0).ns(), copy.specs().get(0).ns());
        assertEquals(to.specs().get(1).type(), copy.specs().get(1).type());
        assertEquals(to.specs().get(1).ns(), copy.specs().get(1).ns());
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
}
