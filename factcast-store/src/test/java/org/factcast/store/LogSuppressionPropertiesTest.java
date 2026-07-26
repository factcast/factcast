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
package org.factcast.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;

class LogSuppressionPropertiesTest {

  @Test
  void testGettersAndSetters() {
    LogSuppressionProperties props = new LogSuppressionProperties();
    props.setEnabled(true);
    props.setMinLogLevel(Level.DEBUG);
    props.setThreshold(500);
    props.setSampleRate(10);

    assertThat(props.isEnabled()).isTrue();
    assertThat(props.getMinLogLevel()).isEqualTo(Level.DEBUG);
    assertThat(props.getThreshold()).isEqualTo(500);
    assertThat(props.getSampleRate()).isEqualTo(10);
  }

  @Test
  void testDefaults() {
    LogSuppressionProperties props = new LogSuppressionProperties();
    assertThat(props.isEnabled()).isFalse();
    assertThat(props.getMinLogLevel()).isEqualTo(Level.INFO);
    assertThat(props.getThreshold()).isEqualTo(1000);
    assertThat(props.getSampleRate()).isEqualTo(1000);
  }
}
