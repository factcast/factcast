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

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.factcast.server.ui.example.ExampleUiServer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    classes = ExampleUiServer.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("uitest")
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@Slf4j
public abstract class AbstractBrowserTest {
  @LocalServerPort protected int port;

  private static BrowserType.LaunchOptions options;
  private static Playwright playwright = null;
  private Browser browser = null;

  // New instance for each test method.
  protected BrowserContext context = null;
  protected Page page = null;

  @BeforeAll
  static void beforeAll() {
    playwright = Playwright.create();
    options = new BrowserType.LaunchOptions();

    if (isRecordPropertySet() || isWatchPropertySet()) {
      long millis = getSlowMotionSpeed().toMillis();
      log.debug("Using " + millis + "ms delay between UI interactions");
      options.setSlowMo(millis);
    }

    options.setHeadless(!isWatchPropertySet());
  }

  private static Duration getSlowMotionSpeed() {
    var watch = System.getProperty("ui.watch");
    if (watch == null || watch.isBlank() || !StringUtils.isNumeric(watch))
      return Duration.ofMillis(500);
    else return Duration.ofMillis(Long.parseLong(watch));
  }

  private static boolean isRecordPropertySet() {
    return System.getProperty("ui.record") != null;
  }

  private static boolean isWatchPropertySet() {
    return System.getProperty("ui.watch") != null;
  }

  @AfterAll
  static void afterAll() {
    playwright.close();
  }

  @BeforeEach
  void createContextAndPage(TestInfo info) {
    Browser.NewContextOptions contextOptions =
        new Browser.NewContextOptions().setViewportSize(1600, 1200);
    if (isRecordPropertySet()) {
      String testPath =
          "target/ui-recording/"
              + info.getTestClass().map(this::retrieveClassName).orElse("Unknown")
              + "_"
              + info.getTestMethod().map(Method::getName).orElse("unknown");
      log.debug("Recording into " + testPath);
      contextOptions.setRecordVideoDir(Path.of(testPath));
    }
    browser = playwright.chromium().launch(options);
    context = browser.newContext(contextOptions);
    page = context.newPage();
  }

  private String retrieveClassName(Class<?> aClass) {
    // we cannot use simpleName here due to nesting
    return aClass.getCanonicalName().replace(aClass.getPackageName() + ".", "");
  }

  @AfterEach
  void closeContext() {
    page.close();
    context.close(); // needed for videos to be saved
    browser.close();
  }

  protected void loginFor(@NonNull String path) {
    final var url = String.join("", "http://localhost:" + port, path);
    page.navigate(url);
    waitForLoadState();

    assertThat(page.locator("h2")).hasText("Log in");

    page.getByLabel("Username").fill("admin");
    page.getByLabel("Password").first().fill("security_disabled");

    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();

    page.waitForURL(url + "?continue");
    waitForLoadState();
  }

  protected Locator jsonView() {
    return page.getByRole(AriaRole.CODE);
  }

  protected void query() {
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Query")).click();
    waitForLoadState();
  }

  private void waitForLoadState() {
    page.waitForLoadState(LoadState.LOAD);
    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    page.waitForLoadState(LoadState.NETWORKIDLE);
  }
}
