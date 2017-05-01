package org.factcast.grpc.api.conv;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.util.FactCastJson;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Fact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact.Builder;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_UUID;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * Converts Protobuf messages to Java Objects and back.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
// TODO add symetry tests
@RequiredArgsConstructor
public class ProtoConverter {

    public MSG_Notification createCatchupNotification() {
        return MSG_Notification.newBuilder().setType(MSG_Notification.Type.Catchup).build();
    }

    public MSG_Notification createCompleteNotification() {
        return MSG_Notification.newBuilder().setType(MSG_Notification.Type.Complete).build();

    }

    public MSG_Notification createNotificationFor(@NonNull Fact t) {
        MSG_Notification.Builder builder = MSG_Notification.newBuilder().setType(
                MSG_Notification.Type.Fact);
        builder.setFact(toProto(t));
        builder.setType(MSG_Notification.Type.Fact);
        return builder.build();

    }

    public MSG_Notification createNotificationFor(@NonNull UUID id) {
        MSG_Notification.Builder builder = MSG_Notification.newBuilder().setType(
                MSG_Notification.Type.Id);
        builder.setId(toProto(id));
        builder.setType(MSG_Notification.Type.Id);
        return builder.build();
    }

    public MSG_UUID toProto(@NonNull UUID id) {
        return MSG_UUID.newBuilder().setLsb(id.getLeastSignificantBits()).setMsb(id
                .getMostSignificantBits()).build();
    }

    @SneakyThrows
    public SubscriptionRequestTO fromProto(@NonNull MSG_SubscriptionRequest request) {
        return FactCastJson.reader().forType(SubscriptionRequestTO.class).readValue(request
                .getJson());
    }

    @SneakyThrows
    public MSG_SubscriptionRequest toProto(SubscriptionRequestTO request) {
        return MSG_SubscriptionRequest.newBuilder().setJson(FactCastJson.writer()
                .writeValueAsString(request)).build();
    }

    public UUID fromProto(MSG_UUID request) {
        long lsb = request.getLsb();
        long msb = request.getMsb();

        return new UUID(msb, lsb);
    }

    public Fact fromProto(MSG_Fact protoFact) {
        return Fact.of(protoFact.getHeader(), protoFact.getPayload());
    }

    public MSG_Fact toProto(Fact factMark) {
        MSG_Fact.Builder proto = MSG_Fact.newBuilder();
        proto.setHeader(factMark.jsonHeader());
        proto.setPayload(factMark.jsonPayload());
        return proto.build();
    }

    public MSG_OptionalFact toProto(Optional<Fact> optFact) {
        Builder proto = MSG_OptionalFact.newBuilder();
        boolean present = optFact.isPresent();

        proto.setPresent(present);
        if (present) {
            proto.setFact(toProto(optFact.get()));
        }
        return proto.build();
    }

    public Optional<Fact> fromProto(@NonNull MSG_OptionalFact message) {
        if (!message.getPresent()) {
            return Optional.empty();
        } else {
            return Optional.of(fromProto(message.getFact()));
        }
    }

}
