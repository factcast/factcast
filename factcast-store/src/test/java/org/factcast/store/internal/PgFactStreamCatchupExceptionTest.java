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
package org.factcast.store.internal;

import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.HighWaterMark;
import org.factcast.core.subscription.observer.HighWaterMarkFetcher;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.catchup.PgCatchup;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.listen.*;
import org.factcast.store.internal.logsuppression.LogSuppression;
import org.factcast.store.internal.pipeline.*;
import org.factcast.store.internal.query.*;
import org.factcast.store.internal.telemetry.PgStoreTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PgFactStreamCatchupExceptionTest {

  @Mock PgConnectionSupplier connectionSupplier;
  @Mock EventBus eventBus;
  @Mock PgFactIdToSerialMapper id2ser;
  @Mock PgCatchupFactory pgCatchupFactory;
  @Mock HighWaterMarkFetcher hwmFetcher;
  @Mock ServerPipeline pipeline;
  @Mock PgStoreTelemetry telemetry;
  @Mock StoreConfigurationProperties props;
  @Mock SubscriptionRequestTO reqTo;
  @Mock LogSuppression logSuppression;

  @InjectMocks @Spy PgFactStream uut;

  @Mock DataSource ds;

  @BeforeEach
  void setup() {
    lenient().when(connectionSupplier.dataSource()).thenReturn(ds);
    lenient().when(reqTo.debugInfo()).thenReturn("test-debug");
    lenient()
        .when(hwmFetcher.highWaterMark(any()))
        .thenReturn(HighWaterMark.of(UUID.randomUUID(), 100L));
    lenient().doReturn(true).when(uut).isConnected();
  }

  @Test
  @SneakyThrows
  void closesDataSourceAndStopsReadingOnCatchupException() {
    ModifiedSingleConnectionDataSource catchupDs = mock(ModifiedSingleConnectionDataSource.class);
    doReturn(catchupDs).when(uut).createCatchupDataSource(any());

    PgCatchup catchup1 = mock(PgCatchup.class);
    PgCatchup catchup2 = mock(PgCatchup.class);
    when(pgCatchupFactory.create(any(), any(), any(), any(), eq(PgCatchupFactory.Phase.PHASE_1)))
        .thenReturn(catchup1);
    lenient()
        .when(
            pgCatchupFactory.create(any(), any(), any(), any(), eq(PgCatchupFactory.Phase.PHASE_2)))
        .thenReturn(catchup2);

    doThrow(new PipelineAlreadyClosedException()).when(catchup1).run();

    uut.doCatchup(); // should fail PHASE1, and not continue to PHASE2

    // Verify that the catchup datasource was closed
    verify(catchupDs).close();

    // Verify that subsequent read operations (phase 2 catchup) were never run because phase 1
    // failed
    // and datasource closed
    verify(catchup2, never()).run();
  }
}
