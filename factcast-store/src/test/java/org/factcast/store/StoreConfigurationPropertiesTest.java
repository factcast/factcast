/*
 * Copyright © 2017-2020 factcast.org
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

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StoreConfigurationPropertiesTest {

  @Test
  void testAfterPropertiesSet() throws Exception {
    StoreConfigurationProperties props = new StoreConfigurationProperties();
    props.setIntegrationTestMode(true);
    props.setSchemaRegistryUrl(null);
    props.setValidationEnabled(false);

    assertThatCode(props::afterPropertiesSet).doesNotThrowAnyException();

    props.setSchemaRegistryUrl("http://localhost");
    props.setValidationEnabled(false);
    assertThatCode(props::afterPropertiesSet).doesNotThrowAnyException();

    props.setValidationEnabled(true);
    assertThatCode(props::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void testLogSuppressionField() {
    StoreConfigurationProperties props = new StoreConfigurationProperties();
    assertThat(props.getLogSuppression()).isNotNull();

    LogSuppressionProperties logProps = new LogSuppressionProperties();
    props.setLogSuppression(logProps);
    assertThat(props.getLogSuppression()).isSameAs(logProps);
  }
}
