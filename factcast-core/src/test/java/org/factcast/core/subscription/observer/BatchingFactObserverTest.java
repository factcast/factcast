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

import static org.mockito.Mockito.*;

import com.google.common.collect.Lists;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchingFactObserverTest {

  private BatchingFactObserver underTest;

  @Nested
  class WhenOningNext {

    @Test
    void batchWithManualFlush() {
      underTest =
          spy(
              new BatchingFactObserver(3) {
                @Override
                public void onNext(List<Fact> batchOfFacts) {}
              });

      TestFact f1 = new TestFact();
      TestFact f2 = new TestFact();
      underTest.onNext(f1);
      underTest.onNext(f2);
      underTest.flush();

      verify(underTest, times(1)).onNext(Lists.newArrayList(f1, f2));
    }

    @Test
    void batchWithAutomaticFlush() {
      underTest =
          spy(
              new BatchingFactObserver(3) {
                @Override
                public void onNext(List<Fact> batchOfFacts) {}
              });

      TestFact f1 = new TestFact();
      TestFact f2 = new TestFact();
      TestFact f3 = new TestFact();
      TestFact f4 = new TestFact();
      underTest.onNext(f1);
      underTest.onNext(f2);
      underTest.onNext(f3);
      underTest.onNext(f4);
      underTest.flush();

      verify(underTest, times(1)).onNext(Lists.newArrayList(f1, f2, f3));
      verify(underTest, times(1)).onNext(Lists.newArrayList(f4));
    }
  }

  @Nested
  class WhenFlushingEmpty {
    @Test
    void skips() {
      underTest =
          spy(
              new BatchingFactObserver(3) {
                @Override
                public void onNext(List<Fact> batchOfFacts) {}
              });

      underTest.flush();

      verify(underTest, never()).onNext(ArgumentMatchers.<List<Fact>>any());
    }
  }

  @Nested
  class WhenCreating {
    @Test
    void doesNotAllow0OrNegativeSize() {
      Assertions.assertThatThrownBy(
              () -> {
                new BatchingFactObserver(0) {
                  @Override
                  public void onNext(List<Fact> batchOfFacts) {}
                };
              })
          .isInstanceOf(IllegalArgumentException.class);

      Assertions.assertThatThrownBy(
              () -> {
                new BatchingFactObserver(-1) {
                  @Override
                  public void onNext(List<Fact> batchOfFacts) {}
                };
              })
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
