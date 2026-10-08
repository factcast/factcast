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

import org.assertj.core.api.Assertions;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.store.internal.PgFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignalTest {

  @Mock private SubscriptionImpl target;

  @Nested
  class Flush {
    Signal underTest = Signal.flush();

    @Test
    void delegates() {
      underTest.pass(target);
      verify(target).flush();
    }

    @Test
    void indicatesFlush() {
      Assertions.assertThat(underTest.indicatesFlush()).isTrue();
    }
  }

  @Nested
  class Catchup {
    Signal underTest = Signal.catchup();

    @Test
    void delegates() {
      underTest.pass(target);
      verify(target).notifyCatchup();
    }

    @Test
    void indicatesFlush() {
      Assertions.assertThat(underTest.indicatesFlush()).isTrue();
    }
  }

  @Nested
  class Complete {
    Signal underTest = Signal.complete();

    @Test
    void delegates() {
      underTest.pass(target);
      verify(target).notifyComplete();
    }

    @Test
    void indicatesFlush() {
      Assertions.assertThat(underTest.indicatesFlush()).isTrue();
    }
  }

  @Nested
  class Position {
    @Mock private FactStreamPosition ffwd;
    Signal underTest;

    @BeforeEach
    void setup() {
      underTest = Signal.of(ffwd);
    }

    @Test
    void delegates() {
      underTest.pass(target);
      verify(target).notifyFastForward(ffwd);
    }

    @Test
    void indicatesFlush() {
      Assertions.assertThat(underTest.indicatesFlush()).isTrue();
    }
  }

  @Nested
  class OfFact {
    @Mock private PgFact fact;
    Signal underTest;

    @BeforeEach
    void setup() {
      underTest = Signal.of(fact);
    }

    @Test
    void delegates() {
      underTest.pass(target);
      verify(target).notifyElement(fact);
    }

    @Test
    void indicatesFlush() {
      Assertions.assertThat(underTest.indicatesFlush()).isFalse();
    }
  }

  @Nested
  class Info {
    @Mock private FactStreamInfo info;

    Signal underTest;

    @BeforeEach
    void setup() {
      underTest = Signal.of(info);
    }

    @Test
    void delegates() {
      underTest.pass(target);
      verify(target).notifyFactStreamInfo(info);
    }

    @Test
    void indicatesFlush() {
      Assertions.assertThat(underTest.indicatesFlush()).isTrue();
    }
  }

  @Nested
  class Error {
    @Mock private Throwable e;
    Signal underTest;

    @BeforeEach
    void setup() {
      underTest = Signal.of(e);
    }

    @Test
    void delegates() {
      underTest.pass(target);
      verify(target).notifyError(e);
    }

    @Test
    void indicatesFlush() {
      Assertions.assertThat(underTest.indicatesFlush()).isTrue();
    }
  }

  @Nested
  class WhenIndicatesingFlush {
    @BeforeEach
    void setup() {}
  }
}
