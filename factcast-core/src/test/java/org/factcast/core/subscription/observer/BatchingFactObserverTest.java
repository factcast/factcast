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
package org.factcast.core.subscription.observer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.Lists;
import java.util.UUID;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.TestFact;
import org.factcast.core.subscription.FactStreamInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchingFactObserverTest {

  @Nested
  class WhenOfing {
    @Mock private FactObserver o;

    @BeforeEach
    void setup() {}

    @Test
    void returnsBridge() {
      Assertions.assertThat(BatchingFactObserver.of(o))
          .isInstanceOf(BatchingFactObserver.Bridge.class);
    }
  }

  @Nested
  class BridgeTest {
    FactObserver o = spy(new FactObserverTest.TestFactObserver());
    BatchingFactObserver.Bridge underTest =
        (BatchingFactObserver.Bridge) BatchingFactObserver.of(o);

    @BeforeEach
    void setup() {}

    @Test
    void onNext() {
      TestFact f1 = new TestFact();
      TestFact f2 = new TestFact();
      underTest.onNext(Lists.newArrayList(f1, f2));

      verify(o, times(2)).onNext(any());
      verify(o).onNext(f1);
      verify(o).onNext(f2);
    }

    @Test
    void onCatchup() {
      underTest.onCatchup();
      verify(o).onCatchup();
    }

    @Test
    void onComplete() {
      underTest.onComplete();
      verify(o).onComplete();
    }

    @Test
    void onError() {
      Exception e = new Exception("ignore me");
      underTest.onError(e);
      verify(o).onError(e);
    }

    @Test
    void onFactStreamInfo() {
      @NonNull FactStreamInfo e = new FactStreamInfo(1L, 2L);
      underTest.onFactStreamInfo(e);
      verify(o).onFactStreamInfo(e);
    }

    @Test
    void onFastForward() {
      @NonNull FactStreamPosition e = FactStreamPosition.of(UUID.randomUUID(), 2L);
      underTest.onFastForward(e);
      verify(o).onFastForward(e);
    }
  }
}
