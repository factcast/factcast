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
package org.factcast.server.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.factcast.server.ui.example.EventInitializer.USER1_EVENT_ID;
import static org.factcast.server.ui.example.EventInitializer.USER4_EVENT_ID;

import com.microsoft.playwright.Page;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Baseline screenshots for comparing the alternative Wicket UI with Vaadin. */
class LayoutParityIntTest extends AbstractBrowserTest {
  @Test
  void queryAndNavigationStillWorkAfterBackendExtraction() {
    loginFor("/ui/full");
    selectNamespace("users");
    fromScratch();
    query();
    // Monaco only renders the visible portion; the newest fact is at the top.
    assertThat(jsonView()).containsText(USER4_EVENT_ID.toString());
    screenshot("query");
    page.navigate("http://localhost:" + port + "/ui/id");
    page.getByLabel("Fact-ID").fill(USER1_EVENT_ID.toString());
    query();
    assertThat(jsonView()).containsText(USER1_EVENT_ID.toString());
    screenshot("id");
    page.navigate("http://localhost:" + port + "/ui/report");
    assertThat(page.getByText("Your Reports", new Page.GetByTextOptions().setExact(true)))
        .isVisible();
    screenshot("reports");
  }

  private void screenshot(String name) {
    page.screenshot(
        new Page.ScreenshotOptions()
            .setPath(Path.of("target/ui-screenshots/vaadin-" + name + ".png"))
            .setFullPage(true));
  }
}
