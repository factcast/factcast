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
package org.factcast.server.ui.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.factcast.server.ui.AbstractBrowserTest;
import org.factcast.server.ui.example.ExampleUiServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext
@SpringBootTest(
    classes = ExampleUiServer.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UIConfigurationIntTest extends AbstractBrowserTest {

  @Test
  void vaadinReactEnabledIsExplicitlySetToFalseViaSystemProperty() {
    assertThat(System.getProperty(UIConfiguration.SYSTEM_PROPERTY_VAADIN_REACT_ENABLE))
        .isEqualTo("false");
  }
}
