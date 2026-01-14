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
import java.util.UUID;
import org.factcast.store.internal.PgConstants;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.core.Notification;

@ExtendWith(MockitoExtension.class)
class FactUpdateNotificationTest {

  static final UUID FACT_ID = UUID.randomUUID();

  @Nested
  class WhenFroming {

    @Test
    void happyPath() {
      Notification n1 =
          new Notification(
              PgConstants.CHANNEL_FACT_UPDATE, 1, "{\"id\":\"%s\"}".formatted(FACT_ID));
      assertThat(Objects.requireNonNull(FactUpdateNotification.from(n1)).uniqueId())
          .isEqualTo(PgConstants.CHANNEL_FACT_UPDATE + "-" + FACT_ID);
    }

    @Test
    void failsOnMissingTxId() {
      Notification n1 = new Notification(PgConstants.CHANNEL_FACT_UPDATE, 1, "{\"ns\":\"hello\"}");
      assertThat(FactUpdateNotification.from(n1)).isNull();
    }
  }

  @Nested
  class WhenUniquingId {
    @Test
    void happyPath() {
      FactUpdateNotification notification = new FactUpdateNotification(FACT_ID);
      assertThat(notification.uniqueId())
          .isEqualTo(PgConstants.CHANNEL_FACT_UPDATE + "-" + FACT_ID);
    }
  }

  private FactUpdateNotificationTest() {}
}
