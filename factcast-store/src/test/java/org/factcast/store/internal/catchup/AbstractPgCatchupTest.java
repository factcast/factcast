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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.pipeline.PushbackServerPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractPgCatchupTest {

  @Mock StoreConfigurationProperties props;
  @Mock PgMetrics metrics;
  @Mock SubscriptionRequestTO req;
  @Mock PushbackServerPipeline pipeline;
  @Mock AtomicLong serial;
  @Mock DataSource ds;
  @Mock PgCatchupFactory.Phase phase;

  private static class DummyCatchup extends AbstractPgCatchup {
    public DummyCatchup(
        StoreConfigurationProperties props,
        PgMetrics metrics,
        SubscriptionRequestTO req,
        PushbackServerPipeline pipeline,
        AtomicLong serial,
        DataSource ds,
        PgCatchupFactory.Phase phase) {
      super(props, metrics, req, pipeline, serial, ds, phase);
    }

    @Override
    public void run() {}
  }

  @Test
  void setsFastForward() {
    DummyCatchup catchup = new DummyCatchup(props, metrics, req, pipeline, serial, ds, phase);
    catchup.fastForward(42L);
    assertThat(catchup.fastForward).isEqualTo(42L);
  }

  @Test
  void nullValidations() {
    assertThrows(
        NullPointerException.class,
        () -> new DummyCatchup(null, metrics, req, pipeline, serial, ds, phase));
    assertThrows(
        NullPointerException.class,
        () -> new DummyCatchup(props, null, req, pipeline, serial, ds, phase));
    assertThrows(
        NullPointerException.class,
        () -> new DummyCatchup(props, metrics, null, pipeline, serial, ds, phase));
    assertThrows(
        NullPointerException.class,
        () -> new DummyCatchup(props, metrics, req, null, serial, ds, phase));
    assertThrows(
        NullPointerException.class,
        () -> new DummyCatchup(props, metrics, req, pipeline, null, ds, phase));
    assertThrows(
        NullPointerException.class,
        () -> new DummyCatchup(props, metrics, req, pipeline, serial, null, phase));
    assertThrows(
        NullPointerException.class,
        () -> new DummyCatchup(props, metrics, req, pipeline, serial, ds, null));
  }
}
