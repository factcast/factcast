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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StagedFactsTest {
  private StagedFacts underTest = new StagedFacts(150);

  @Test
  void smallerTargetStillAllowsOneFactWithinTheInboundLimit() {
    Fact fact =
        Fact.of(
            "{\"ns\":\"foo\",\"id\":\"" + UUID.randomUUID() + "\"}", "{\"value\":\"1234567890\"}");
    StagedFacts staged = new StagedFacts(150, 50);

    Assertions.assertThat(staged.add(fact)).isTrue();
    Assertions.assertThat(staged.add(fact)).isFalse();
    Assertions.assertThat(staged.popAll()).containsExactly(fact);
    Assertions.assertThat(staged.add(fact)).isTrue();
  }

  @Test
  void factAboveTheClientLimitFailsExplicitly() {
    Fact fact =
        Fact.of(
            "{\"ns\":\"foo\",\"id\":\"" + UUID.randomUUID() + "\"}",
            "{\"value\":\"" + "x".repeat(200) + "\"}");
    StagedFacts staged = new StagedFacts(150, 50);

    assertThatThrownBy(() -> staged.add(fact))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(
            error ->
                Assertions.assertThat(((StatusRuntimeException) error).getStatus().getCode())
                    .isEqualTo(Status.Code.RESOURCE_EXHAUSTED));
    Assertions.assertThat(staged.isEmpty()).isTrue();
  }

  @Test
  void rejectsInvalidBatchTarget() {
    assertThatThrownBy(() -> new StagedFacts(150, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StagedFacts(150, 91)).isInstanceOf(IllegalArgumentException.class);
  }

  @Nested
  class WhenAdding {
    @Mock private Fact fact;

    @BeforeEach
    void setup() {}

    @Test
    void adheresToMaxByteLimit() {
      Fact f =
          Fact.of(
              "{\"ns\":\"foo\",\"id\":\"" + UUID.randomUUID() + "\",\"c\":100}",
              "{\"value\":\"1234567890\"}");
      Assertions.assertThat(underTest.add(f)).isTrue();
      Assertions.assertThat(underTest.size()).isOne();

      // should not be added, as the limit would be exceeded:
      Assertions.assertThat(underTest.add(f)).isFalse();
      Assertions.assertThat(underTest.size()).isOne();
    }
  }

  @Nested
  class WhenCheckingIfIsEmpty {
    @BeforeEach
    void setup() {}

    @Test
    void delegates() {

      Assertions.assertThat(underTest.isEmpty()).isTrue();

      Fact f =
          Fact.of(
              "{\"ns\":\"foo\",\"id\":\"" + UUID.randomUUID() + "\",\"c\":100}",
              "{\"value\":\"1234567890\"}");
      underTest.add(f);
      Assertions.assertThat(underTest.isEmpty()).isFalse();

      underTest.popAll();

      Assertions.assertThat(underTest.isEmpty()).isTrue();
    }
  }

  @Nested
  class WhenSizing {
    @BeforeEach
    void setup() {}

    @SuppressWarnings("JoinAssertThatStatements")
    @Test
    void delegates() {
      underTest = new StagedFacts(225);
      Fact f =
          Fact.of(
              "{\"ns\":\"foo\",\"id\":\"" + UUID.randomUUID() + "\",\"c\":100}",
              "{\"value\":\"1234567890\"}");
      Assertions.assertThat(underTest.add(f)).isTrue();
      Assertions.assertThat(underTest.add(f)).isTrue();
      Assertions.assertThat(underTest.add(f)).isFalse();

      Assertions.assertThat(underTest.popAll()).hasSize(2);

      Assertions.assertThat(underTest.size()).isZero();
      // check that the capacity for two more facts is avail
      Assertions.assertThat(underTest.add(f)).isTrue();
      Assertions.assertThat(underTest.add(f)).isTrue();
    }
  }

  @Nested
  class WhenPopingAll {
    @BeforeEach
    void setup() {}

    @Test
    void clearsAndResetsByteCount() {

      Assertions.assertThat(underTest.size()).isZero();

      Fact f =
          Fact.of(
              "{\"ns\":\"foo\",\"id\":\"" + UUID.randomUUID() + "\",\"c\":100}",
              "{\"value\":\"1234567890\"}");
      underTest.add(f);
      Assertions.assertThat(underTest.size()).isOne();

      underTest.popAll();

      Assertions.assertThat(underTest.size()).isZero();
    }
  }
}
