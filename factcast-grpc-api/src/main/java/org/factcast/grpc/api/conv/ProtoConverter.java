/**
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

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.util.FactCastJson;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Empty;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Fact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact.Builder;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalSerial;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ProtocolVersion;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_UUID;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Converts Protobuf messages to Java Objects and back.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@RequiredArgsConstructor
public class ProtoConverter {

    private static final MSG_Empty EMPTY = MSG_Empty.newBuilder().build();

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
        return MSG_UUID.newBuilder()
                .setLsb(id.getLeastSignificantBits())
                .setMsb(id
                        .getMostSignificantBits())
                .build();
    }

    public SubscriptionRequestTO fromProto(@NonNull MSG_SubscriptionRequest request) {
        return FactCastJson.readValue(SubscriptionRequestTO.class, request.getJson());
    }

    public MSG_SubscriptionRequest toProto(SubscriptionRequestTO request) {
        return MSG_SubscriptionRequest.newBuilder()
                .setJson(FactCastJson.writeValueAsString(
                        request))
                .build();
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

    @NonNull
    public OptionalLong fromProto(@NonNull MSG_OptionalSerial serialOf) {
        // note that an unsigned is used to transport the serial. Serials MUST be >0
        if (serialOf.getPresent() && serialOf.getSerial() > 0) {
            return OptionalLong.of(serialOf.getSerial());
        } else {
            return OptionalLong.empty();
        }
    }

    @NonNull
    public ProtocolVersion fromProto(@NonNull MSG_ProtocolVersion msg) {
        return ProtocolVersion.of(msg.getMajor(), msg.getMinor(), msg.getPatch());
    }

    @NonNull
    public MSG_ProtocolVersion toProto(@NonNull ProtocolVersion v) {
        return MSG_ProtocolVersion.newBuilder()
                .setMajor(v.major())
                .setMinor(v.minor())
                .setPatch(v.patch())
                .build();
    }

    public MSG_Empty empty() {
        return EMPTY;
    }

}
