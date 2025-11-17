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
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.server.ui.AbstractBrowserTest;
import org.factcast.server.ui.views.filter.FactCriteria;
import org.junitpioneer.jupiter.RetryingTest;

@Slf4j
class ReportQueryPageIntTest extends AbstractBrowserTest {

  final ObjectMapper om = new ObjectMapper();

  @RetryingTest(maxAttempts = 3)
  @SneakyThrows
  void createReportHappyPath() {
    om.registerModule(new JavaTimeModule());
    om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    // ARRANGE
    loginFor("/ui/report");
    assertThat(page.getByLabel("Types")).isDisabled();
    final var ns = "users";
    selectNamespace(ns);

    // types input now enabled
    assertThat(page.getByLabel("Types")).isEnabled();
    final var type = "UserCreated";
    selectTypes(List.of(type));
    fromScratch();

    final var id = UUID.randomUUID();
    setReportName(id.toString());

    // ACT
    generate();

    // ASSERT

    final var expectedReportFilename = id + ".json";
    assertThat(page.getByText(expectedReportFilename)).hasCount(1);

    page.getByText(id + ".json").click();

    final var download =
        page.waitForDownload(
            () ->
                page.waitForPopup(
                    () ->
                        page.getByRole(
                                AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Download"))
                            .click()));

    final var eventId1 = UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec084");
    final var userId1 = UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec085");
    final var eventId2 = UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec086");
    final var userId2 = UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec087");
    final var eventId3 = UUID.fromString("7ea100c6-9175-423d-b7e2-2d9bc66e328f");
    final var userId3 = UUID.fromString("fe3d3a2e-9b36-4b1b-8e68-406f4b37c70d");
    final var eventId4 = UUID.fromString("82369346-3b82-4703-9803-d91ae71d0b7e");
    final var userId4 = UUID.fromString("07ff11b5-437e-4ab8-92c8-e5d29ed376d4");

    try (final var input = download.createReadStream()) {
      final var report = om.readTree(input.readAllBytes());
      assertThat(report.get("name").asText()).isEqualTo(id + ".json");
      assertThat(report.get("events")).hasSize(4);

      // events are sorted from old to new
      assertJsonEvent(report.get("events").get(0), eventId1, userId1, type, "Peter", "Lustig");
      assertJsonEvent(report.get("events").get(1), eventId2, userId2, type, "Werner", "Ernst");
      assertJsonEvent(report.get("events").get(2), eventId3, userId3, type, "Dillon", "Keller");
      assertJsonEvent(report.get("events").get(3), eventId4, userId4, type, "Edwin", "Jäger");

      log.info(report.get("query").toString());
      final var query = om.readValue(report.get("query").toString(), ReportFilterBean.class);
      assertThat(query.getDefaultFrom()).isEqualTo(4);
      assertThat(query.getFrom()).isNull();
      assertThat(query.getCriteria()).hasSize(1);
      assertCriterion(query.getCriteria().get(0), ns, type, null);

      final var timestamp =
          om.treeToValue((JsonNode) report.get("generatedAt"), OffsetDateTime.class);
      // Just checking it's actually a date
      assertThat(timestamp.getDayOfMonth()).isEqualTo(OffsetDateTime.now().getDayOfMonth());
    }
  }

  @RetryingTest(maxAttempts = 3)
  @SneakyThrows
  void generateAndDeleteReport() {
    // ARRANGE
    loginFor("/ui/report");
    assertThat(page.getByLabel("Types")).isDisabled();
    final var ns = "users";
    selectNamespace(ns);

    assertThat(page.getByLabel("Types")).isEnabled();
    final var type = "UserCreated";
    selectTypes(List.of(type));
    fromScratch();

    final var id = UUID.randomUUID();
    setReportName(id.toString());
    final var expectedReportFilename = id + ".json";
    final var expectedReportItem = page.getByText(expectedReportFilename);

    // ACT
    generate();
    assertThat(expectedReportItem).hasCount(1); // just to make sure
    expectedReportItem.click();
    delete();

    // ASSERT
    page.reload();
    assertThat(expectedReportItem).hasCount(0);
  }

  @RetryingTest(maxAttempts = 3)
  @SneakyThrows
  void generateButtonStaysEnabledIfPreviousAttemptDidNotProduceReport() {
    // ARRANGE
    loginFor("/ui/report");
    assertThat(page.getByLabel("Types")).isDisabled();
    final var ns = "users";
    selectNamespace(ns);

    // types input now enabled
    assertThat(page.getByLabel("Types")).isEnabled();
    final var type = "UserCreated";
    selectTypes(List.of(type));

    page.getByLabel("Starting Serial").fill("5"); // too far, should not find anything

    final var id = UUID.randomUUID();
    setReportName(id.toString());

    // ACT
    generate();

    // ASSERT
    final var expectedReportFilename = id + ".json";
    // no report created, no file created
    assertThat(page.getByText(expectedReportFilename)).hasCount(0);
    // chosen report file name is still valid as name for new report,
    // users will probably adjust filter criteria and try to generate report again immediately
    assertThat(getButton("Generate")).isEnabled();
  }

  private void assertJsonEvent(
      JsonNode event, UUID eventId, UUID userId, String type, String firstName, String lastName) {
    assertThat(event.get("header").get("type").asText()).isEqualTo(type);
    assertThat(event.get("header").get("id").asText()).isEqualTo(eventId.toString());
    assertThat(event.get("payload").get("userId").asText()).isEqualTo(userId.toString());
    assertThat(event.get("payload").get("firstName").asText()).isEqualTo(firstName);
    assertThat(event.get("payload").get("lastName").asText()).isEqualTo(lastName);
  }

  private void assertCriterion(FactCriteria criterion, String ns, String type, UUID aggId) {
    assertThat(criterion.getNs()).isEqualTo(ns);
    assertThat(criterion.getType()).containsExactly(type);
    assertThat(criterion.getAggId()).isEqualTo(aggId);
  }

  private void setReportName(String name) {
    page.getByLabel("Report File Name").fill(name);
  }

  private void generate() {
    clickButton("Generate");
  }

  private void delete() {
    clickButton("Delete");
  }
}
