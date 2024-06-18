/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.security.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;

@ExtendWith(MockitoExtension.class)
class FactCastUserTest {
  @Mock private User user;
  @Mock private FactCastAccount account;
  @Spy private Map<String, Boolean> readAccess = new HashMap<>();
  @Spy private Map<String, Boolean> writeAccess = new HashMap<>();
  private FactCastUser underTest;

  @Nested
  class WhenCheckingIfCanRead {
    private final String NS = "NS";
    private final String NS_CAN_READ = "NSR";

    @BeforeEach
    void setup() {
      when(account.id()).thenReturn("abc");
      underTest = new FactCastUser(account, "secret");
    }

    @Test
    void delegates() {
      when(account.canRead(NS)).thenReturn(false);
      when(account.canRead(NS_CAN_READ)).thenReturn(true);

      Assertions.assertThat(underTest.canRead(NS)).isFalse();
      Assertions.assertThat(underTest.canRead(NS_CAN_READ)).isTrue();
    }

    @Test
    void caches() {
      when(account.canRead(NS)).thenReturn(false);

      Assertions.assertThat(underTest.canRead(NS)).isFalse();
      // should be answered from cache
      Assertions.assertThat(underTest.canRead(NS)).isFalse();
      Assertions.assertThat(underTest.canRead(NS)).isFalse();

      verify(account, times(1)).canRead(NS);
    }
  }

  @Nested
  class WhenCheckingIfCanWrite {
    private final String NS = "NS";
    private final String NS_CAN_WRITE = "NSW";

    @BeforeEach
    void setup() {
      when(account.id()).thenReturn("abc");
      underTest = new FactCastUser(account, "secret");
    }

    @Test
    void delegates() {
      when(account.canWrite(NS)).thenReturn(false);
      when(account.canWrite(NS_CAN_WRITE)).thenReturn(true);

      Assertions.assertThat(underTest.canWrite(NS)).isFalse();
      Assertions.assertThat(underTest.canWrite(NS_CAN_WRITE)).isTrue();
    }

    @Test
    void caches() {
      when(account.canWrite(NS)).thenReturn(false);

      Assertions.assertThat(underTest.canWrite(NS)).isFalse();
      // should be answered from cache
      Assertions.assertThat(underTest.canWrite(NS)).isFalse();
      Assertions.assertThat(underTest.canWrite(NS)).isFalse();

      verify(account, times(1)).canWrite(NS);
    }
  }
}
