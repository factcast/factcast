/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.store.internal.pipeline;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import lombok.NonNull;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.store.internal.PgFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerPipelineAdapterTest {
  @Mock private SubscriptionImpl sub;
  @InjectMocks private ServerPipelineAdapter underTest;

  @Nested
  class WhenFacting {
    @Mock private PgFact fact;

    @BeforeEach
    void setup() {}

    @Test
    void delegates() {
      underTest.process(Signal.of(fact));
      verify(sub).notifyElement(fact);
      verifyNoMoreInteractions(sub);
    }
  }

  @Nested
  class WhenInfoing {
    @Mock private @NonNull FactStreamInfo info;

    @BeforeEach
    void setup() {}

    @Test
    void delegates() {
      underTest.process(Signal.of(info));
      verify(sub).notifyFactStreamInfo(info);
      verifyNoMoreInteractions(sub);
    }
  }

  @Nested
  class WhenFastingForward {
    @Mock private @NonNull FactStreamPosition ffwd;

    @BeforeEach
    void setup() {}

    @Test
    void delegates() {
      underTest.process(Signal.of(ffwd));
      verify(sub).notifyFastForward(ffwd);
      verifyNoMoreInteractions(sub);
    }
  }

  @Nested
  class WhenErroring {
    @Mock private @NonNull Throwable err;

    @BeforeEach
    void setup() {}

    @Test
    void delegates() {
      underTest.process(Signal.of(err));
      verify(sub).notifyError(err);
      verifyNoMoreInteractions(sub);
    }
  }

  @Nested
  class WhenCatchuping {
    @BeforeEach
    void setup() {}

    @Test
    void delegates() {
      underTest.process(Signal.catchup());
      verify(sub).notifyCatchup();
      verifyNoMoreInteractions(sub);
    }
  }

  @Nested
  class WhenCompleting {
    @BeforeEach
    void setup() {}

    @Test
    void delegates() {
      underTest.process(Signal.complete());
      verify(sub).notifyComplete();
      verifyNoMoreInteractions(sub);
    }
  }
}
