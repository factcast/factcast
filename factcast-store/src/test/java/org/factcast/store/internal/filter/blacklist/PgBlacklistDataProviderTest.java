/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.internal.filter.blacklist;

import static org.mockito.Mockito.*;

import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import java.util.*;
import lombok.SneakyThrows;
import org.factcast.store.internal.listen.PgListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PgBlacklistDataProviderTest {
  @Spy private EventBus bus = new EventBus();
  @Mock private JdbcTemplate jdbc;
  @Mock private Blacklist blacklist;
  @InjectMocks private PgBlacklistDataProvider underTest;

  @Nested
  class WhenAfteringSingletonsInstantiated {
    @Test
    void fetches() {
      underTest.afterSingletonsInstantiated();
      verify(blacklist).accept(any());
    }

    @Test
    void registersOnBus() {
      underTest.afterSingletonsInstantiated();
      verify(blacklist, times(1)).accept(any());
      // three more
      bus.post(new PgListener.BlacklistChangeSignal());
      bus.post(new PgListener.BlacklistChangeSignal());
      bus.post(new PgListener.BlacklistChangeSignal());
      verify(blacklist, times(4)).accept(any());
    }
  }

  @Nested
  class WhenDisposing {
    @SneakyThrows
    @Test
    void unregisters() {
      underTest.afterSingletonsInstantiated();
      underTest.destroy();
      verify(bus).unregister(underTest);
    }
  }

  @Nested
  class WhenUpdatingBlacklist {
    private final UUID FACT_ID = UUID.randomUUID();
    private final UUID NEW_FACT_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
      when(jdbc.queryForList("SELECT id FROM blacklist", UUID.class))
          .thenReturn(List.of(FACT_ID), List.of(NEW_FACT_ID, FACT_ID), Collections.emptyList());
      underTest.afterSingletonsInstantiated();
    }

    @Test
    void updatesSet() {
      underTest.on(new PgListener.BlacklistChangeSignal());
      verify(blacklist).accept(Sets.newHashSet(NEW_FACT_ID, FACT_ID));

      underTest.on(new PgListener.BlacklistChangeSignal());
      verify(blacklist).accept(new HashSet<>());
    }
  }

  @Nested
  class WhenOning {
    @Mock private PgListener.BlacklistChangeSignal signal;

    @Test
    void fetches() {
      underTest.on(signal);
      verify(jdbc).queryForList("SELECT id FROM blacklist", UUID.class);
      verify(blacklist).accept(any());
    }
  }
}
