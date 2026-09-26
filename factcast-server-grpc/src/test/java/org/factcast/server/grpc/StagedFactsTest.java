/*
 * Copyright © 2017-2024 factcast.org
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

import static org.assertj.core.api.Assertions.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Fact;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.junit.jupiter.api.Test;

class StagedFactsTest {

  private static MSG_Fact fact(String payload) {
    return MSG_Fact.newBuilder()
        .setHeader("{\"ns\":\"äöü\",\"id\":\"00000000-0000-0000-0000-000000000001\"}")
        .setPayload(payload)
        .build();
  }

  private static int notificationSize(MSG_Fact... facts) {
    return MSG_Notification.newBuilder()
        .setType(MSG_Notification.Type.Facts)
        .setFacts(MSG_Facts.newBuilder().addAllFact(java.util.List.of(facts)))
        .build()
        .getSerializedSize();
  }

  @Test
  void countsTheExactSerializedNotificationIncludingMultibyteJson() {
    StagedFacts staged = new StagedFacts(1024);
    MSG_Fact first = fact("{\"value\":\"€😀\"}");
    MSG_Fact second = fact("{\"value\":\"" + "ü".repeat(80) + "\"}");

    assertThat(staged.add(first)).isTrue();
    assertThat(staged.currentBytes()).isEqualTo(notificationSize(first));
    assertThat(staged.add(second)).isTrue();
    assertThat(staged.currentBytes()).isEqualTo(notificationSize(first, second));
    assertThat(staged.popAll().getSerializedSize()).isEqualTo(notificationSize(first, second));
    assertThat(staged.currentBytes()).isZero();
    assertThat(staged.size()).isZero();
  }

  @Test
  void acceptsAnExactBatchBoundaryAndRejectsTheNextFactWithoutChangingIt() {
    MSG_Fact fact = fact("{\"value\":\"boundary\"}");
    int twoFacts = notificationSize(fact, fact);
    int inbound = twoFacts;
    while (inbound - inbound / 10 < twoFacts) {
      inbound++;
    }
    StagedFacts staged = new StagedFacts(inbound);

    assertThat(staged.add(fact)).isTrue();
    assertThat(staged.add(fact)).isTrue();
    assertThat(staged.currentBytes()).isEqualTo(twoFacts);
    assertThat(staged.add(fact)).isFalse();
    assertThat(staged.currentBytes()).isEqualTo(twoFacts);
    assertThat(staged.popAll().getFacts().getFactCount()).isEqualTo(2);
    assertThat(staged.isEmpty()).isTrue();
    assertThat(staged.add(fact)).isTrue();
  }

  @Test
  void sendsOneFactAtTheExactInboundLimitEvenAboveTheBatchTarget() {
    MSG_Fact large = fact("{\"value\":\"" + "x".repeat(500) + "\"}");
    int inbound = notificationSize(large);
    StagedFacts staged = new StagedFacts(inbound);

    assertThat(staged.add(large)).isTrue();
    assertThat(staged.currentBytes()).isEqualTo(inbound);
    assertThat(staged.popAll().getSerializedSize()).isEqualTo(inbound);
  }

  @Test
  void failsExplicitlyWhenOneFactExceedsTheInboundLimit() {
    MSG_Fact large = fact("{\"value\":\"" + "x".repeat(500) + "\"}");
    int inbound = notificationSize(large) - 1;
    StagedFacts staged = new StagedFacts(inbound);

    assertThatThrownBy(() -> staged.add(large))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(
            error ->
                assertThat(((StatusRuntimeException) error).getStatus().getCode())
                    .isEqualTo(Status.Code.RESOURCE_EXHAUSTED));
    assertThat(staged.isEmpty()).isTrue();
  }
}
