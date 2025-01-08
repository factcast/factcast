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
package org.factcast.server.ui.report;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;
import java.util.UUID;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.factcast.server.ui.AbstractBrowserTest;
import org.junitpioneer.jupiter.RetryingTest;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
class ReportQueryPageIntTest extends AbstractBrowserTest {

  @RetryingTest(maxAttempts = 3)
  @SneakyThrows
  void happyPath() {
    loginFor("/ui/report");
    assertThat(page.getByLabel("Types")).isDisabled();
    selectNamespace("users");

    // types input now enabled
    assertThat(page.getByLabel("Types")).isEnabled();
    selectTypes(List.of("UserCreated"));
    fromScratch();

    final var id = UUID.randomUUID();
    setReportName(id.toString());
    generate();

    page.getByText(id + ".json").click();

    Download download =
        page.waitForDownload(
            () ->
                page.waitForPopup(
                    () ->
                        page.getByRole(
                                AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Download"))
                            .click()));

    var om = new ObjectMapper();
    try (final var input = download.createReadStream()) {
      final JsonNode report = om.readTree(input.readAllBytes());
      Assertions.assertThat(report.get("name").asText()).isEqualTo(id + ".json");
      Assertions.assertThat(report.get("events").get(0).get("header").get("type").asText())
          .isEqualTo("UserCreated");
      Assertions.assertThat(report.get("events").get(0).get("payload").get("firstName").asText())
          .isEqualTo("Werner");
      final var query = om.readValue(report.get("query").toString(), ReportFilterBean.class);
      Assertions.assertThat(query.getDefaultFrom()).isEqualTo(2);
      Assertions.assertThat(query.getCriteria().get(0).getNs()).isEqualTo("users");
      log.info(report.get("query").toString());
      // TODO: assert query
    } catch (Exception e) {
      throw e;
    }
  }

  // TODO Add test for deletion

  private void setReportName(String name) {
    page.getByLabel("Report File Name").fill(name);
  }

  private void generate() {
    clickButton("Generate");
  }
}
