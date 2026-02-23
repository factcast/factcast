/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.catchup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.StoreConfigurationProperties.CatchupStrategy;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.catchup.chunked.PgChunkedCatchup;
import org.factcast.store.internal.catchup.cursor.PgCursorCatchup;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PgCatchUpFactoryImplTest {

  @Mock StoreConfigurationProperties props;
  @Mock PgMetrics metrics;

  @Mock SubscriptionRequestTO request;
  @Mock ServerPipeline pipeline;
  @Mock AtomicLong serial;
  @Mock CurrentStatementHolder holder;
  @Mock DataSource ds;

  @InjectMocks PgCatchUpFactoryImpl underTest;

  @Nested
  class Create {

    @Test
    void returnsFetchingInPhase2RegardlessOfStrategy() {
      when(props.getCatchupStrategy()).thenReturn(CatchupStrategy.CURSOR);
      underTest = new PgCatchUpFactoryImpl(props, metrics);

      // even if CHUNKED is configured, PHASE_2 must use cursor
      var result =
          underTest.create(request, pipeline, serial, holder, ds, PgCatchupFactory.Phase.PHASE_2);

      assertThat(result).isInstanceOf(PgCursorCatchup.class);
    }

    @Test
    void returnsChunkedInPhase1WhenStrategyIsChunked() {
      when(props.getCatchupStrategy()).thenReturn(CatchupStrategy.CHUNKED);

      var result =
          underTest.create(request, pipeline, serial, holder, ds, PgCatchupFactory.Phase.PHASE_1);

      assertThat(result).isInstanceOf(PgChunkedCatchup.class);
    }

    @Test
    void returnsFetchingInPhase1WhenStrategyIsFetching() {
      when(props.getCatchupStrategy()).thenReturn(CatchupStrategy.CURSOR);

      var result =
          underTest.create(request, pipeline, serial, holder, ds, PgCatchupFactory.Phase.PHASE_1);

      assertThat(result).isInstanceOf(PgCursorCatchup.class);
    }
  }
}
