package org.factcast.grpc.api.conv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.Test0Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Empty;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalSerial;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.junit.Test;

public class ProtoConverter0Test {

    ProtoConverter uut = new ProtoConverter();

    @Test
    public void testToProtoUUID() throws Exception {
        UUID probe = UUID.randomUUID();
        UUID copy = uut.fromProto(uut.toProto(probe));

        assertEquals(probe, copy);
    }

    @Test(expected = NullPointerException.class)
    public void testToProtoUUIDNull() throws Exception {
        uut.toProto((UUID) null);
    }

    @Test(expected = NullPointerException.class)
    public void testToProtoFactNull() throws Exception {
        uut.toProto((Fact) null);
    }

    @Test(expected = NullPointerException.class)
    public void testFromProtoOptionalNull() throws Exception {
        uut.fromProto((MSG_OptionalFact) null);
    }

    @Test
    public void testToProtoFact() throws Exception {

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
    public void testToOptionalProtoFactEmpty() throws Exception {
        Optional<Fact> probe = Optional.empty();
        Optional<Fact> copy = uut.fromProto(uut.toProto(probe));

        assertFalse(copy.isPresent());
    }

    @Test
    public void testToOptionalProtoFact() throws Exception {
        Optional<Fact> probe = Optional.of(new Test0Fact().ns("oink"));
        Optional<Fact> copy = uut.fromProto(uut.toProto(probe));

        assertTrue(copy.isPresent());
        assertEquals(probe.get().ns(), copy.get().ns());
    }

    @Test
    public void testCreateCatchupNotification() throws Exception {
        MSG_Notification n = uut.createCatchupNotification();

        assertNotNull(n);
        assertEquals(MSG_Notification.Type.Catchup, n.getType());
    }

    @Test
    public void testCreateCompleteNotification() throws Exception {
        MSG_Notification n = uut.createCompleteNotification();

        assertNotNull(n);
        assertEquals(MSG_Notification.Type.Complete, n.getType());
    }

    @Test(expected = NullPointerException.class)
    public void testCreateNotificationForFactNull() throws Exception {
        uut.createNotificationFor((Fact) null);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateNotificationForIdNull() throws Exception {
        uut.createNotificationFor((UUID) null);
    }

    @Test
    public void testCreateNotificationForFact() throws Exception {
        final Test0Fact probe = new Test0Fact().ns("123");
        MSG_Notification n = uut.createNotificationFor(probe);

        assertNotNull(n);
        assertEquals(MSG_Notification.Type.Fact, n.getType());
        assertEquals(probe.ns(), uut.fromProto(n.getFact()).ns());

    }

    @Test
    public void testCreateNotificationForUUID() throws Exception {
        final UUID probe = UUID.randomUUID();
        MSG_Notification n = uut.createNotificationFor(probe);

        assertNotNull(n);
        assertEquals(MSG_Notification.Type.Id, n.getType());
        assertEquals(probe, uut.fromProto(n.getId()));

    }

    @Test(expected = NullPointerException.class)
    public void testFromProtoMSG_SubscriptionRequestNull() throws Exception {
        uut.fromProto((MSG_SubscriptionRequest) null);
    }

    @Test(expected = NullPointerException.class)
    public void testToProtoSubscriptionRequestNull() throws Exception {
        uut.toProto((SubscriptionRequestTO) null);
    }

    @Test
    public void testToProtoSubscriptionRequest() throws Exception {
        SubscriptionRequestTO to = new SubscriptionRequestTO().continuous(true)
                .ephemeral(false)
                .debugInfo("test")
                .maxBatchDelayInMs(13)
                .marks(true);

        to.addSpecs(Arrays.asList(FactSpec.ns("foo")));
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
    public void testToProtoOptionalLongPresent() throws Exception {
        MSG_OptionalSerial msg = uut.toProto(OptionalLong.of(133));
        assertTrue(msg.getPresent());
        assertEquals(133, msg.getSerial());

    }

    @Test
    public void testToProtoOptionalLongEmpty() throws Exception {
        MSG_OptionalSerial msg = uut.toProto(OptionalLong.empty());
        assertFalse(msg.getPresent());
    }

    @Test
    public void testEmpty() throws Exception {
        assertEquals(MSG_Empty.newBuilder().build(), uut.empty());
    }
}
