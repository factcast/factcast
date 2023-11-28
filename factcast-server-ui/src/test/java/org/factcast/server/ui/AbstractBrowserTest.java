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
package org.factcast.server.ui;

import static org.assertj.core.api.Assertions.*;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import jakarta.annotation.Nullable;
import java.util.Collection;
import lombok.NonNull;
import org.factcast.server.ui.example.ExampleUiServer;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    classes = ExampleUiServer.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@IntegrationTest
public abstract class AbstractBrowserTest {
  @LocalServerPort protected int port;

  private static Playwright playwright = null;
  private static Browser browser = null;

  // New instance for each test method.
  protected BrowserContext context = null;
  protected Page page = null;

  @BeforeAll
  static void beforeAll() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch();
    //    .launch(new BrowserType.LaunchOptions().setHeadless(false).setSlowMo(20));
  }

  @AfterAll
  static void afterAll() {
    playwright.close();
  }

  @BeforeEach
  void createContextAndPage() {
    context =
        browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200)); // new
    // Browser.NewContextOptions().setRecordVideoDir(Path.of("target"))
    page = context.newPage();
  }

  @AfterEach
  void closeContext() {
    context.close();
  }

  protected void login() {
    navigateTo("/login");

    page.navigate("http://localhost:" + port);

    assertThat(page.waitForSelector("h1").innerText()).contains("FactCast Server UI");

    page.getByLabel("Username").fill("admin");
    page.getByLabel("Password").first().fill("security_disabled");

    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();

    assertThat(page.waitForSelector("h2").innerText()).contains("Query");
  }

  protected void navigateTo(@Nullable String path) {
    page.navigate(String.join("", "http://localhost:" + port, path));
  }

  protected void selectNamespace(@NonNull String ns) {
    selectNamespace(ns, 0);
  }

  protected void selectNamespace(@NonNull String ns, int index) {
    page.getByLabel("Namespace").nth(index).click();

    final var attr =
        page.locator("#namespace-selector")
            .nth(index)
            .locator("input")
            .getAttribute("aria-controls");

    final var options = page.locator("#" + attr);
    options.waitFor();
    options.getByRole(AriaRole.OPTION, new Locator.GetByRoleOptions().setName(ns)).click();
  }

  protected void selectTypes(@NonNull Collection<String> types) {
    selectTypes(types, 0);
  }

  protected void selectTypes(@NonNull Collection<String> types, int index) {
    page.getByLabel("Types").nth(index).click();

    final var input = page.locator("#types-selector").nth(index).locator("input");
    final var attr = input.getAttribute("aria-controls");
    final var options = page.locator("#" + attr);
    options.waitFor();

    types.forEach(
        t -> options.getByRole(AriaRole.OPTION, new Locator.GetByRoleOptions().setName(t)).click());

    input.blur();
  }

  protected Locator openSerialSelector() {
    page.locator("#starting-serial > input").click();
    final var dialog = page.getByRole(AriaRole.DIALOG);
    dialog.waitFor();
    return dialog;
  }

  protected void query() {
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Query")).click();
  }
}
