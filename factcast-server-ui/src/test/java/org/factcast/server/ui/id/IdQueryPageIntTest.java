/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.ui.id;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.factcast.server.ui.example.EventInitializer.USER1_EVENT_ID;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.UUID;
import lombok.NonNull;
import org.factcast.server.ui.AbstractBrowserTest;
import org.junit.jupiter.api.Nested;
import org.junitpioneer.jupiter.RetryingTest;

class IdQueryPageIntTest extends AbstractBrowserTest {

  @Nested
  class Basics {
    @RetryingTest(maxAttempts = 5, minSuccess = 1)
    void queryById() {
      loginFor("/ui/id");

      final var versionSelector = page.getByLabel("Version");
      assertThat(versionSelector).isDisabled();

      setId(USER1_EVENT_ID);

      assertThat(versionSelector).isEnabled();
      assertThat(versionSelector).containsText("as published");

      query();

      assertThat(jsonView()).containsText(USER1_EVENT_ID.toString());
    }

    @RetryingTest(maxAttempts = 5, minSuccess = 1)
    void queryByIdAndVersion() {
      loginFor("/ui/id");

      setId(UUID.randomUUID());
      assertThat(page.getByLabel("Version")).isDisabled();

      setId(USER1_EVENT_ID);
      assertThat(page.getByLabel("Version")).isEnabled();
      setVersion(3);

      query();

      assertThat(jsonView()).containsText(USER1_EVENT_ID.toString());
      assertThat(jsonView()).containsText("displayName");
      assertThat(jsonView()).containsText("Peter Lustig");
      assertThat(jsonView()).containsText("\"version\" : 3");
    }
  }

  private void setVersion(int version) {
    page.locator("#version-selector vaadin-input-container div").click();
    page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(String.valueOf(version)))
        .locator("div")
        .click();
  }

  private void setId(@NonNull UUID id) {
    page.getByLabel("Fact-ID").fill(id.toString());
  }
}
