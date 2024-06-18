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
package org.factcast.core.subscription.observer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import lombok.NonNull;
import nl.altindag.console.ConsoleCaptor;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.TestFactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class FactObserverTest {

  static class TestFactObserver implements FactObserver {
    @Override
    public void onNext(@NonNull Fact element) {}
  }

  @Spy Logger logger = LoggerFactory.getLogger(FactObserver.class);
  @InjectMocks private TestFactObserver underTest;

  @Nested
  class WhenCallingOnMethod {
    @Mock private @NonNull Fact element;

    @Test
    void noEffect() {
      FactStreamPosition ffwd = TestFactStreamPosition.random();
      FactStreamInfo info = new FactStreamInfo(1, 2);
      // none of them can throw an exception
      underTest.onNext(element);
      underTest.onFactStreamInfo(info);
      underTest.onFastForward(ffwd);
      underTest.onCatchup();
      underTest.onComplete();

      try (ConsoleCaptor consoleCaptor = new ConsoleCaptor()) {
        IOException e = new IOException();
        underTest.onError(e);
        assertThat(
                consoleCaptor.getErrorOutput().stream()
                    .filter(s -> s.contains("Unhandled onError:")))
            .hasSize(1);
      }
    }
  }
}
