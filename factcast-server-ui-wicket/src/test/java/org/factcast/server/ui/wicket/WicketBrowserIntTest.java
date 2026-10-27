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
package org.factcast.server.ui.wicket;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.*;
import java.util.*;
import org.factcast.server.ui.wicket.example.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;

@Tag("uitest")
@SpringBootTest(
    classes = ExampleWicketServer.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "factcast.security.enabled=false",
      "factcast.store.schema-registry-url=classpath:example-registry"
    })
class WicketBrowserIntTest {
  @TempDir static Path directory;
  @LocalServerPort int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("factcast.ui.report.store.path", () -> directory.toString());
  }

  @Test
  void queriesTransformsExportsAndManagesReports() throws Exception {
    try (var playwright = Playwright.create()) {
      var launch = new BrowserType.LaunchOptions().setHeadless(true);
      if (System.getProperty("ui.browser") != null)
        launch.setExecutablePath(Path.of(System.getProperty("ui.browser")));
      try (var browser = playwright.chromium().launch(launch);
          var context =
              browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 1000))) {
        var page = context.newPage();
        var errors = new ArrayList<String>();
        page.onPageError(errors::add);
        try {
          page.navigate("http://localhost:" + port + "/login");
          screenshot(page, "login");
          page.getByLabel("Username").fill("alice");
          page.getByLabel("Password").fill("security_disabled");
          page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();
          assertThat(page.getByLabel("Types")).isDisabled();
          page.getByLabel("Namespace", new Page.GetByLabelOptions().setExact(true))
              .selectOption(new com.microsoft.playwright.options.SelectOption().setLabel("users"));
          assertThat(page.getByLabel("Types")).containsText("UserCreated");
          assertThat(page.getByLabel("Types")).isEnabled();
          page.getByLabel("Types")
              .selectOption(
                  new com.microsoft.playwright.options.SelectOption().setLabel("UserCreated"));
          page.getByLabel("Starting Serial").fill("0");
          page.getByLabel("Starting Serial").press("Enter");
          assertThat(page.locator(".json-tree")).containsText("Peter");
          assertThat(page.locator(".fact")).hasCount(4);
          screenshot(page, "query");
          var export =
              page.waitForDownload(
                  () ->
                      page.getByRole(
                              AriaRole.LINK, new Page.GetByRoleOptions().setName("Export JSON"))
                          .click());
          assertThat(Files.readString(export.path())).contains("Peter", "Werner");
          String queryLink = page.url();
          page.reload();
          assertThat(page.getByLabel("Namespace", new Page.GetByLabelOptions().setExact(true)))
              .hasValue("0");
          assertThat(page.getByLabel("Starting Serial")).hasValue("0");
          assertThat(queryLink).contains("state=");
          page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Query by Fact-ID"))
              .click();
          page.getByLabel("Fact-ID", new Page.GetByLabelOptions().setExact(true))
              .fill(EventInitializer.USER1_EVENT_ID.toString());
          page.getByRole(
                  AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Query").setExact(true))
              .click();
          assertThat(page.locator(".json-tree")).containsText("Peter");
          assertThat(page.getByLabel("Version")).containsText("2");
          page.getByLabel("Version")
              .selectOption(new com.microsoft.playwright.options.SelectOption().setLabel("2"));
          page.getByRole(
                  AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Query").setExact(true))
              .click();
          assertThat(page.locator(".json-tree")).containsText("Peter");
          screenshot(page, "id");
          page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Report Generation"))
              .click();
          page.getByLabel("Namespace", new Page.GetByLabelOptions().setExact(true))
              .selectOption(new com.microsoft.playwright.options.SelectOption().setLabel("users"));
          page.getByLabel("Starting Serial").fill("0");
          page.getByLabel("Report File Name").fill("browser-report");
          page.getByLabel("Report File Name").press("Enter");
          assertThat(page.locator("#report-table")).containsText("browser-report.json");
          screenshot(page, "reports");
          var report =
              page.waitForDownload(
                  () ->
                      page.getByRole(
                              AriaRole.LINK,
                              new Page.GetByRoleOptions().setName("Download").setExact(true))
                          .click());
          assertThat(Files.readString(report.path())).contains("Peter", "events");
          page.getByRole(
                  AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Delete").setExact(true))
              .click();
          assertThat(page.locator("#report-table")).not().containsText("browser-report.json");
          page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Logout")).click();
          assertThat(page.getByLabel("Username")).isVisible();
          assertThat(errors).isEmpty();
        } catch (Throwable failure) {
          screenshot(page, "failure");
          Files.writeString(Path.of("target/ui-screenshots/failure.html"), page.content());
          throw new AssertionError(
              "Browser workflow failed at " + page.url() + "; JavaScript errors: " + errors,
              failure);
        }
      }
    }
  }

  private void screenshot(Page page, String screen) {
    page.screenshot(
        new Page.ScreenshotOptions()
            .setPath(Path.of("target/ui-screenshots/wicket-" + screen + ".png"))
            .setFullPage(true));
  }
}
