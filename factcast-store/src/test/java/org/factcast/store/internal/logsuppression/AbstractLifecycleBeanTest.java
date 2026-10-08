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
package org.factcast.store.internal.logsuppression;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AbstractLifecycleBeanTest {

  private int started = 0;
  private int stopped = 0;

  private final AbstractLifecycleBean underTest =
      new AbstractLifecycleBean() {
        @Override
        protected void onStart() {
          started++;
        }

        @Override
        protected void onStop() {
          stopped++;
        }
      };

  @Test
  void testLifecycle() {
    assertThat(underTest.isRunning()).isFalse();

    underTest.start();
    assertThat(underTest.isRunning()).isTrue();
    assertThat(started).isEqualTo(1);

    underTest.start(); // Should be idempotent
    assertThat(underTest.isRunning()).isTrue();
    assertThat(started).isEqualTo(1);

    underTest.stop();
    assertThat(underTest.isRunning()).isFalse();
    assertThat(stopped).isEqualTo(1);

    underTest.stop(); // Should be idempotent
    assertThat(underTest.isRunning()).isFalse();
    assertThat(stopped).isEqualTo(1);
  }
}
