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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.factcast.core.Fact;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.util.FactCastJson;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.StateForRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ConditionalPublishRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ConditionalPublishResult;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_CurrentDatabaseTime;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Empty;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Fact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalFact.Builder;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_OptionalSerial;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ServerConfig;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ServerProperties;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_ServerProtocolVersion;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_StateForRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_String;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_StringSet;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_UUID;

import com.google.protobuf.ProtocolStringList;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Converts Protobuf messages to Java Objects and back.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
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
        MSG_Notification.Builder builder = MSG_Notification.newBuilder()
                .setType(MSG_Notification.Type.Fact);
        builder.setFact(toProto(t));
        return builder.build();
    }

    public MSG_Notification createNotificationFor(@NonNull UUID id) {
        MSG_Notification.Builder builder = MSG_Notification.newBuilder()
                .setType(MSG_Notification.Type.Id);
        builder.setId(toProto(id));
        return builder.build();
    }

    @NonNull
    public MSG_UUID toProto(@NonNull UUID id) {
        return MSG_UUID.newBuilder()
                .setLsb(id.getLeastSignificantBits())
                .setMsb(id.getMostSignificantBits())
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

    public MSG_Empty empty() {
        return EMPTY;
    }

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

    public Set<String> fromProto(MSG_StringSet set) {
        ProtocolStringList sList = set.getEmbeddedStringList();
        return new HashSet<>(sList);
    }

    public MSG_StringSet toProto(Set<String> set) {
        return MSG_StringSet.newBuilder().addAllEmbeddedString(set).build();
    }

    public MSG_String toProto(String ns) {
        return MSG_String.newBuilder().setEmbeddedString(ns).build();
    }

    public String fromProto(MSG_String request) {
        return request.getEmbeddedString();
    }

    public @NonNull MSG_Facts toProto(@NonNull List<? extends Fact> toPublish) {
        MSG_Facts.Builder ret = MSG_Facts.newBuilder();
        for (Fact fact : toPublish) {
            ret.addFact(toProto(fact));
        }
        return ret.build();
    }

    public StateForRequest fromProto(MSG_StateForRequest request) {
        List<UUID> aggIds = request.getAggIdsList()
                .stream()
                .map(this::fromProto)
                .collect(Collectors.toList());
        String ns = request.getNsPresent() ? request.getNs() : null;
        return new StateForRequest(aggIds, ns);
    }

    public ConditionalPublishRequest fromProto(@NonNull MSG_ConditionalPublishRequest request) {
        UUID token = null;
        if (request.getTokenPresent()) {
            token = fromProto(request.getToken());
        }

        return new ConditionalPublishRequest(fromProto(request.getFacts()), token);
    }

    public @NonNull List<? extends Fact> fromProto(@NonNull MSG_Facts facts) {
        return facts.getFactList().stream().map(this::fromProto).collect(Collectors.toList());
    }

    public MSG_ConditionalPublishResult toProto(boolean result) {
        return MSG_ConditionalPublishResult.newBuilder().setSuccess(result).build();
    }

    public MSG_StateForRequest toProto(@NonNull StateForRequest req) {
        String ns = req.ns();
        MSG_StateForRequest.Builder b = MSG_StateForRequest.newBuilder()
                .setNsPresent(ns != null)
                .addAllAggIds(req.aggIds()
                        .stream()
                        .map(this::toProto)
                        .collect(Collectors.toList()));

        if (ns != null) {
            b.setNs(ns);
        }
        return b.build();

    }

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

    public long fromProto(@NonNull MSG_CurrentDatabaseTime resp) {
        return resp.getMillis();
    }

    public MSG_CurrentDatabaseTime toProto(long currentTime) {
        return MSG_CurrentDatabaseTime.newBuilder().setMillis(currentTime).build();
    }
}
