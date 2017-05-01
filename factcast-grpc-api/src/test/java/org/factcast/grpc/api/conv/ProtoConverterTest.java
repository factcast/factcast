package org.factcast.grpc.api.conv;

import static org.junit.Assert.*;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.junit.Test;

public class ProtoConverterTest {

    ProtoConverter uut = new ProtoConverter();

    @Test
    public void testToProtoUUID() throws Exception {
        UUID probe = UUID.randomUUID();
        UUID copy = uut.fromProto(uut.toProto(probe));

        assertEquals(probe, copy);
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
        Fact probe = new TestFact().aggId(aggId).jsonPayload(payload).meta(key1, value1).meta(key2,
                value2).type(type).ns(ns);

        Fact copy = uut.fromProto(uut.toProto(probe));

        assertEquals(probe.id(), copy.id());
        assertEquals(probe.aggId(), copy.aggId());
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
        Optional<Fact> probe = Optional.of(new TestFact().ns("oink"));
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

    @Test
    public void testCreateNotificationForFact() throws Exception {
        final TestFact probe = new TestFact().ns("123");
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
}
