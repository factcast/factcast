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
package org.factcast.server.ui.full;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.factcast.server.ui.example.EventInitializer.*;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.NonNull;
import org.factcast.server.ui.AbstractBrowserTest;
import org.junit.jupiter.api.*;

class FullQueryPageIntTest extends AbstractBrowserTest {
  @BeforeEach
  void setUp() {
    login();
    navigateTo("/ui/full");
  }

  @Nested
  class Basics {
    @Test
    void queryByType() {
      // types input disabled
      assertThat(page.getByLabel("Types")).isDisabled();

      selectNamespace("users");

      // types input now enabled
      assertThat(page.getByLabel("Types")).isEnabled();

      selectTypes(List.of("UserCreated"));
      fromScratch();

      query();

      assertThat(jsonView()).containsText(USER1_EVENT_ID.toString());
      assertThat(jsonView()).containsText(USER2_EVENT_ID.toString());
    }

    @Test
    void queryByAggId() {
      selectNamespace("users");
      setAggId(USER2_AGG_ID);
      fromScratch();

      query();

      assertThat(jsonView()).containsText(USER2_EVENT_ID.toString());

      page.screenshot();
    }

    @Test
    void queryByMeta() {
      selectNamespace("users");
      addMetaEntry("hugo", "bar"); // that's user 1
      assertMetaCount(1);
      fromScratch();

      query();

      assertThat(jsonView()).containsText(USER1_EVENT_ID.toString());
    }

    @Test
    void queryByLimit() {
      selectNamespace("users");
      page.getByLabel("Limit").fill("1");
      fromScratch();

      query();

      assertThat(jsonView()).containsText(USER1_EVENT_ID.toString());
    }

    @Test
    void queryByOffset() {
      selectNamespace("users");
      page.getByLabel("Offset").fill("1");
      fromScratch();

      query();

      assertThat(jsonView()).containsText(USER2_EVENT_ID.toString());
    }

    @Test
    void queryFromLatest() {
      selectNamespace("users");

      fromLatest();

      query();

      assertThat(jsonView()).not().containsText(USER1_EVENT_ID.toString());
      assertThat(jsonView()).not().containsText(USER2_EVENT_ID.toString());
    }

    private void assertMetaCount(int count) {
      assertThat(
              page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Meta"))
                  .locator("[slot='suffix']"))
          .hasText(String.valueOf(count));
    }
  }

  @Nested
  class MultiCondition {
    @Test
    void queryByTypeAndAggIds() {
      selectNamespace("users");
      setAggId(USER1_AGG_ID);

      addNewCondition();

      selectNamespace("users", 1);
      selectTypes(List.of("UserCreated"), 1);
      setAggId(USER2_AGG_ID, 1);

      fromScratch();

      query();

      assertThat(jsonView()).containsText(USER1_EVENT_ID.toString());
      assertThat(jsonView()).containsText(USER2_EVENT_ID.toString());
    }

    @Test
    void queryByAggIdAndMeta() {
      selectNamespace("users");
      setAggId(USER2_AGG_ID);

      addNewCondition();

      selectNamespace("users", 1);
      addMetaEntry("hugo", "bar", 1); // that's user 1

      fromScratch();

      query();

      assertThat(jsonView()).containsText(USER1_EVENT_ID.toString());
      assertThat(jsonView()).containsText(USER2_EVENT_ID.toString());
    }

    private void addNewCondition() {
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("add Condition")).click();
    }
  }

  @Nested
  class JsonView {
    @Test
    void codeLensIsPresent() {
      // setup result
      selectNamespace("users");
      fromScratch();
      query();

      // codelens is visible
      assertThat(
              jsonView()
                  .locator("[widgetid*='codelens']")
                  .filter(new Locator.FilterOptions().setHasText("Name: Werner"))
                  .first())
          .containsText("Name: Werner");

      assertThat(
              jsonView()
                  .locator("[widgetid*='codelens']")
                  .filter(new Locator.FilterOptions().setHasText("Name: Peter"))
                  .first())
          .containsText("Name: Peter");
    }
  }

  private void addMetaEntry(@NonNull String key, @NonNull String value) {
    addMetaEntry(key, value, 0);
  }

  private void addMetaEntry(@NonNull String key, @NonNull String value, int index) {
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Meta")).nth(index).click();

    final var dialog = page.getByRole(AriaRole.DIALOG);
    dialog.waitFor();
    dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Add")).click();

    final var addDialog =
        page.getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setIncludeHidden(true)).nth(1);
    addDialog.getByLabel("Key").fill(key);
    addDialog.getByLabel("Value").fill(value);
    addDialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Add")).click();

    dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Close")).click();
  }

  private void fromScratch() {
    openSerialSelector()
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("From scratch"))
        .click();
  }

  private void fromLatest() {
    openSerialSelector()
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Latest serial"))
        .click();
  }

  private void setAggId(@NonNull UUID aggId) {
    setAggId(aggId, 0);
  }

  private void setAggId(@NonNull UUID aggId, int index) {
    page.getByLabel("Aggregate-ID").nth(index).fill(aggId.toString());
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
}
