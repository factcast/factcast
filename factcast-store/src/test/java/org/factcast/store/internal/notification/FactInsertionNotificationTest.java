/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.store.internal.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Assertions;
import org.factcast.store.internal.PgConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.core.Notification;

@ExtendWith(MockitoExtension.class)
class FactInsertionNotificationTest {

  @Nested
  class WhenUniquingId {
    @BeforeEach
    void setup() {}

    @Test
    void happyPath() {
      FactInsertionNotification probe = new FactInsertionNotification("ns", "type", 1L);
      Assertions.assertThat(probe.uniqueId()).isEqualTo(PgConstants.CHANNEL_FACT_INSERT + "-1");
    }

    @Test
    void noUniqueIdIfSerUnknown() {
      FactInsertionNotification probe = new FactInsertionNotification("ns", "type", null);
      Assertions.assertThat(probe.uniqueId()).isNull();
    }
  }

  @Nested
  class WhenDistributeding {

    @Test
    void internalIsNotDistributed() {
      Assertions.assertThat(FactInsertionNotification.internal().distributed()).isFalse();
    }

    @Test
    void distributedIfSerialKnown() {
      FactInsertionNotification probe = new FactInsertionNotification("ns", "type", 1L);
      Assertions.assertThat(probe.distributed()).isTrue();
    }
  }

  @Nested
  class WhenFroming {

    @Test
    void failsOnMissingSerial() {
      Notification n1 =
          new Notification(
              PgConstants.CHANNEL_FACT_INSERT, 1, "{\"ns\":\"ns1\",\"type\":\"type1\"}");
      assertThat(FactInsertionNotification.from(n1)).isNull();
    }

    @Test
    void happyPath() {
      Notification n1 =
          new Notification(
              PgConstants.CHANNEL_FACT_INSERT, 1, "{\"ns\":\"ns1\",\"type\":\"type1\",\"ser\":1}");
      assertThat(FactInsertionNotification.from(n1))
          .isEqualTo(new FactInsertionNotification("ns1", "type1", 1L));
    }
  }

  @Nested
  class WhenNsAndType {

    @Test
    void equals() {
      FactInsertionNotification probe1 = new FactInsertionNotification("ns", "type", 1L);
      FactInsertionNotification probe2 = new FactInsertionNotification("ns", "type", 2L);
      Assertions.assertThat(probe1.nsAndType()).isEqualTo(probe2.nsAndType());
    }

    @Test
    void differsOnNs() {
      FactInsertionNotification probe1 = new FactInsertionNotification("ns", "type", 1L);
      FactInsertionNotification probe2 = new FactInsertionNotification("ns2", "type", 2L);
      Assertions.assertThat(probe1.nsAndType()).isNotEqualTo(probe2.nsAndType());
    }

    @Test
    void differsOnType() {
      FactInsertionNotification probe1 = new FactInsertionNotification("ns", "type", 1L);
      FactInsertionNotification probe2 = new FactInsertionNotification("ns", "type2", 2L);
      Assertions.assertThat(probe1.nsAndType()).isNotEqualTo(probe2.nsAndType());
    }
  }
}
