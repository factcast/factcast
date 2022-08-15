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
package org.factcast.store.internal.catchup.tmppaged;

import static org.mockito.Mockito.*;

import java.sql.PreparedStatement;
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

@ExtendWith(MockitoExtension.class)
class PgCatchUpFetchTmpPageTest {

  private static final int PAGE_SIZE = 85;
  @Mock private @NonNull JdbcTemplate jdbc;
  @Mock private @NonNull SubscriptionRequestTO req;
  @Mock private @NonNull CurrentStatementHolder statementHolder;

  @Nested
  class WhenFetchingFacts {
    @Mock private @NonNull AtomicLong serial;
    PgCatchUpFetchTmpPage underTest;

    @BeforeEach
    void setup() {
      underTest = new PgCatchUpFetchTmpPage(jdbc, PAGE_SIZE, req, statementHolder);
    }

    @SneakyThrows
    @Test
    void setsStatementToHolder() {
      ArgumentCaptor<PreparedStatementSetter> captor =
          ArgumentCaptor.forClass(PreparedStatementSetter.class);

      underTest.fetchFacts(serial);

      verify(jdbc).query(Mockito.anyString(), captor.capture(), Mockito.any(PgFactExtractor.class));
      PreparedStatementSetter value = captor.getValue();
      verify(statementHolder).clear();

      PreparedStatement ps = mock(PreparedStatement.class);
      value.setValues(ps);

      verify(statementHolder).statement(same(ps));
      verifyNoMoreInteractions(statementHolder);
    }
  }
}
