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
class BlacklistChangeNotificationTest {

  @Nested
  class WhenFroming {

    @Test
    void failsOnMissingTxId() {
      Notification n1 = new Notification(PgConstants.CHANNEL_BLACKLIST_CHANGE, 1, "{}");
      assertThat(BlacklistChangeNotification.from(n1)).isNull();
    }

    @Test
    void happyPath() {
      Assertions.assertThat(
              BlacklistChangeNotification.from(
                  new Notification(PgConstants.CHANNEL_BLACKLIST_CHANGE, 1, "{\"txId\":1}")))
          .isNotNull()
          .extracting("txId")
          .isEqualTo(1L);
    }
  }

  @Nested
  class WhenUniquingId {
    @Test
    void name() {
      BlacklistChangeNotification from =
          BlacklistChangeNotification.from(
              new Notification(PgConstants.CHANNEL_BLACKLIST_CHANGE, 1, "{\"txId\":7}"));
      Assertions.assertThat(from.uniqueId()).isEqualTo("blacklist_change-7");
    }
  }

  @Nested
  class WhenDistributing {

    @Test
    void internalIsNotDistributed() {
      Assertions.assertThat(BlacklistChangeNotification.internal().distributed()).isFalse();
    }

    @Test
    void distributedIfSerialKnown() {
      BlacklistChangeNotification probe = new BlacklistChangeNotification(1L);
      Assertions.assertThat(probe.distributed()).isTrue();
    }
  }
}
