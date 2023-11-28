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

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;
import org.factcast.server.ui.AbstractBrowserTest;
import org.junit.jupiter.api.*;

class FullQueryIntTest extends AbstractBrowserTest {

  @Test
  void queryByNamespaceAndTypeFromScratch() {
    login();

    navigateTo("/ui/full");

    // types input disabled
    assertThat(page.getByLabel("Types")).isDisabled();

    // select users ns via combobox
    selectNamespace("users");

    // types input now enabled
    assertThat(page.getByLabel("Types")).isEnabled();

    // select UserCreated type via multi select box
    selectTypes(List.of("UserCreated"));

    // open the serial helper popup and chose From scratch
    openSerialSelector()
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("From scratch"))
        .click();

    query();

    assertThat(page.getByRole(AriaRole.CODE)).containsText("UserCreated");
    assertThat(page.getByRole(AriaRole.CODE)).containsText("Werner");
    assertThat(page.getByRole(AriaRole.CODE)).containsText("Ernst");
  }
}
