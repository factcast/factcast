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

import java.util.Objects;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.store.internal.PgConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.PGNotification;
import org.postgresql.core.Notification;

@ExtendWith(MockitoExtension.class)
class SchemaStoreChangeNotificationTest {

  @Nested
  class WhenUniquingId {
    @Test
    void happyPath() {
      Notification n1 =
          new Notification(
              PgConstants.CHANNEL_SCHEMASTORE_CHANGE,
              1,
              "{\"ns\":\"ns1\",\"type\":\"type1\",\"version\":3,\"txId\":1}");
      assertThat(Objects.requireNonNull(SchemaStoreChangeNotification.from(n1)).uniqueId())
          .isEqualTo(PgConstants.CHANNEL_SCHEMASTORE_CHANGE + "-ns1-type1-3-1");
    }
  }

  @Nested
  class WhenFroming {
    @Mock private @NonNull PGNotification n;

    @Test
    void failsOnMissingTxId() {
      Notification n1 =
          new Notification(
              PgConstants.CHANNEL_SCHEMASTORE_CHANGE,
              1,
              "{\"ns\":\"ns1\",\"type\":\"type1\",\"version\":3}");
      assertThat(SchemaStoreChangeNotification.from(n1)).isNull();
    }

    @Test
    void happyPath() {
      Notification n1 =
          new Notification(
              PgConstants.CHANNEL_SCHEMASTORE_CHANGE,
              1,
              "{\"ns\":\"ns1\",\"type\":\"type1\",\"version\":3,\"txId\":1}");
      assertThat(SchemaStoreChangeNotification.from(n1))
          .isEqualTo(new SchemaStoreChangeNotification("ns1", "type1", 3, 1L));
    }
  }

  @Nested
  class WhenDistributing {

    @Test
    void internalIsNotDistributed() {
      Assertions.assertThat(SchemaStoreChangeNotification.internal().distributed()).isFalse();
    }

    @Test
    void distributedIfSerialKnown() {
      SchemaStoreChangeNotification probe = new SchemaStoreChangeNotification("ns", "type", 1, 1L);
      Assertions.assertThat(probe.distributed()).isTrue();
    }
  }
}
