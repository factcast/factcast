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
package org.factcast.factus.projector;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.UUID;
import org.factcast.core.Fact;
import org.factcast.factus.projection.Projection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractTransactionalLensTest {

  static class TestLens extends AbstractTransactionalLens {
    public TestLens(Projection projection) {
      super(projection);
    }

    @Override
    protected void doClear() {}

    @Override
    protected void doFlush() {}
  }

  static class TestProjection implements Projection {}

  final TestProjection p = new TestProjection();
  final TestLens underTest = spy(new TestLens(p));

  @Nested
  class WhenBeforeFactProcessing {
    @Mock private Fact f;

    @BeforeEach
    void setup() {}

    @Test
    void resetsTimeIfBatching() {

      underTest.bulkSize(100);
      underTest.start().set(0L);

      underTest.beforeFactProcessing(f);

      assertThat(underTest.start().get())
          .isNotEqualTo(0L)
          .isLessThanOrEqualTo(System.currentTimeMillis())
          .isGreaterThan(System.currentTimeMillis() - 1000);
    }
  }

  @Nested
  class WhenAfterFactProcessing {
    @Mock private Fact f;

    @BeforeEach
    void setup() {}

    @Test
    void counts() {
      when(underTest.shouldFlush()).thenReturn(false);

      int before = underTest.count().get();
      underTest.afterFactProcessing(f);

      assertThat(underTest.count().get()).isEqualTo(before + 1);
    }
  }

  @Nested
  class WhenOnCatchup {
    @Mock private Projection p;

    @BeforeEach
    void setup() {}

    @Test
    void doesNotUnnecessarilyflush() {

      underTest.onCatchup(p);

      verify(underTest, never()).flush();
    }

    @Test
    void flushesOnCacthupIfNecessary() {

      underTest.bulkSize(30);

      // mark it dirty

      underTest.afterFactProcessing(Fact.builder().id(UUID.randomUUID()).buildWithoutPayload());

      underTest.onCatchup(p);

      verify(underTest, times(1)).flush();
    }

    @Test
    void disablesBatching() {

      underTest.bulkSize(20);
      underTest.onCatchup(p);

      assertThat(underTest.bulkSize()).isEqualTo(1);
    }
  }

  @Nested
  class WhenSkippingStateUpdate {
    @BeforeEach
    void setup() {}

    @Test
    void calculatesStateSkipping() {

      when(underTest.shouldFlush(anyBoolean())).thenReturn(false, false, true, true);
      when(underTest.isBulkApplying()).thenReturn(false, true, false, true);

      assertThat(underTest.skipStateUpdate()).isFalse();
      assertThat(underTest.skipStateUpdate()).isTrue();
      assertThat(underTest.skipStateUpdate()).isFalse();
      assertThat(underTest.skipStateUpdate()).isTrue();
    }
  }

  @Nested
  class WhenFlushing {
    @BeforeEach
    void setup() {}

    @Test
    void resetsClock() {

      underTest.start().set(System.currentTimeMillis());
      underTest.bulkSize(10);
      underTest.flush();

      assertThat(underTest.start().get()).isEqualTo(0);
    }

    @Test
    void delegates() {
      underTest.flush();
      verify(underTest).doFlush();
    }
  }

  @Nested
  class WhenAfteringFactProcessingFailed {
    @Mock private Fact f;
    @Mock private Throwable justForInformation;

    @BeforeEach
    void setup() {}

    @Test
    void rollsback() {

      underTest.bulkSize(100);
      underTest.afterFactProcessingFailed(f, new IOException("oh dear"));
      verify(underTest).doClear();
    }
  }
}
