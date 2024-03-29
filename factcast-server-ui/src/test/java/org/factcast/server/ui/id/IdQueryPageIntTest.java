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
      setId(USER1_EVENT_ID);

      query();

      assertThat(jsonView()).containsText(USER1_EVENT_ID.toString());
    }

    @RetryingTest(maxAttempts = 5, minSuccess = 1)
    void queryByIdAndVersion() {
      loginFor("/ui/id");
      setId(USER1_EVENT_ID);
      setVersion(3);

      query();

      assertThat(jsonView()).containsText(USER1_EVENT_ID.toString());
      assertThat(jsonView()).containsText("displayName");
      assertThat(jsonView()).containsText("Peter Lustig");
    }
  }

  private void setVersion(int version) {
    page.getByLabel("Version").fill(String.valueOf(version));
  }

  private void setId(@NonNull UUID id) {
    page.getByLabel("Fact-ID").fill(id.toString());
  }
}
