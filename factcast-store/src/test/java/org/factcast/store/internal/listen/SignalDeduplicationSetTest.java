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
package org.factcast.store.internal.listen;

import static org.assertj.core.api.Assertions.*;

import org.factcast.store.internal.listen.PgListener.Signal;
import org.junit.jupiter.api.*;

class SignalDeduplicationSetTest {

  @Test
  void dedups() {
    SignalDeduplicationSet uut = new SignalDeduplicationSet(2);
    assertThat(uut.add(new Signal("", "", "", "1"))).isTrue();
    assertThat(uut.add(new Signal("", "", "", "1"))).isFalse();
    assertThat(uut.add(new Signal("", "", "", "1"))).isFalse();
  }

  @Test
  void trims() {
    SignalDeduplicationSet uut = new SignalDeduplicationSet(2);
    assertThat(uut.add(new Signal("", "", "", "1"))).isTrue();
    assertThat(uut.add(new Signal("", "", "", "2"))).isTrue();
    assertThat(uut.add(new Signal("", "", "", "3"))).isTrue();

    // two and three should be in
    assertThat(uut.add(new Signal("", "", "", "2"))).isFalse();
    assertThat(uut.add(new Signal("", "", "", "3"))).isFalse();
    // 1 should have been trimmed
    assertThat(uut.add(new Signal("", "", "", "1"))).isTrue();
  }
}
