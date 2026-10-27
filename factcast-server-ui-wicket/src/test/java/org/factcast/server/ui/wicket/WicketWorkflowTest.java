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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.apache.wicket.util.tester.WicketTester;
import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;
import org.factcast.server.security.auth.*;
import org.factcast.server.ui.id.IdQueryBean;
import org.factcast.server.ui.plugins.*;
import org.factcast.server.ui.port.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class WicketWorkflowTest {
  private WicketTester tester;
  private FactRepository repository;
  private ReportStore reports;
  private JsonViewPluginService plugins;

  @BeforeEach
  void setup() {
    repository = mock(FactRepository.class);
    reports = mock(ReportStore.class);
    plugins = mock(JsonViewPluginService.class);
    when(repository.namespaces(any())).thenReturn(List.of("users", "orders"));
    when(repository.types(eq("users"), any())).thenReturn(List.of("UserCreated"));
    when(repository.fetchChunk(any())).thenReturn(List.of());
    when(plugins.process(anyList())).thenReturn(new JsonViewEntries(List.of()));
    when(plugins.getNonResponsivePlugins()).thenReturn(List.of());
    login();
    tester = new WicketTester(new FactCastWicketApplication(repository, reports, plugins));
  }

  private void login() {
    var user = new FactCastUser(FactCastAccount.GOD, "unused");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
  }

  @AfterEach
  void cleanup() {
    tester.destroy();
    SecurityContextHolder.clearContext();
  }

  @Test
  void rendersNavigationAndAllThreeScreens() {
    tester.startPage(QueryPage.class);
    tester.assertContains("FactCast Server UI");
    tester.assertContains("Query by Fact-ID");
    tester.assertContains("Report Generation");
    tester.clickLink("id");
    tester.assertRenderedPage(IdPage.class);
    tester.clickLink("reports");
    tester.assertRenderedPage(ReportPage.class);
  }

  @Test
  void anonymousRequestsGoToLogin() {
    SecurityContextHolder.clearContext();
    tester.startPage(QueryPage.class);
    tester.assertRenderedPage(LoginPage.class);
  }

  @Test
  void invalidUuidDoesNotReachTheRepository() {
    tester.startPage(IdPage.class);
    var form = tester.newFormTester("form");
    form.setValue("id", "not-a-uuid");
    form.submit("query");
    assertThat(tester.getFeedbackMessages(null)).isNotEmpty();
    verify(repository, never()).findBy(any());
  }

  @Test
  void retrievesFactAndOffersTransformations() {
    UUID id = UUID.randomUUID();
    var fact =
        Fact.of(
            "{\"id\":\"" + id + "\",\"ns\":\"users\",\"type\":\"UserCreated\",\"version\":1}",
            "{\"name\":\"Ada\"}");
    when(repository.findBy(any())).thenReturn(Optional.of(fact));
    when(repository.versions("users", "UserCreated")).thenReturn(List.of(1, 2));
    var node = FactCastJson.toObjectNode("{\"header\":{},\"payload\":{\"name\":\"Ada\"}}");
    when(plugins.process(any(Fact.class)))
        .thenReturn(new JsonViewEntry(node, new JsonEntryMetaData()));
    tester.startPage(IdPage.class);
    var form = tester.newFormTester("form");
    form.setValue("id", id.toString());
    form.submit("query");
    tester.assertContains("Ada");
    verify(repository)
        .findBy(argThat((IdQueryBean b) -> id.equals(b.getId()) && b.getVersion() == 0));
  }

  @Test
  void resetClearsResultsAndRestoresDefaults() {
    tester.startPage(QueryPage.class);
    tester.clickLink("form:reset");
    tester.assertContains("Starting Serial");
    tester.assertInvisible("form:export");
  }

  @Test
  void reportGenerationRequiresAName() {
    tester.startPage(ReportPage.class);
    var form = tester.newFormTester("form");
    form.submit("generate");
    assertThat(tester.getFeedbackMessages(null)).isNotEmpty();
    verify(reports, never()).createBatchUpload(any(), any(), any());
  }

  @Test
  void queriesWithNamespaceAndExportsResults() {
    tester.startPage(QueryPage.class);
    var form = tester.newFormTester("form");
    form.select("filters:conditions:0:namespace", 0);
    form.setValue("filters:from", "0");
    form.submit("query");
    tester.assertNoErrorMessage();
    verify(repository)
        .fetchChunk(
            argThat(
                b ->
                    "users".equals(b.getCriteria().getFirst().getNs())
                        && b.resolveFromOrZero() == 0));
    tester.assertVisible("form:export");
  }

  @Test
  void rejectsFractionalSerialsBeforeQuerying() {
    tester.startPage(QueryPage.class);
    var form = tester.newFormTester("form");
    form.select("filters:conditions:0:namespace", 0);
    form.setValue("filters:from", "1.5");
    form.submit("query");
    tester.assertContains("Serials must be whole numbers");
    verify(repository, never()).fetchChunk(any());
  }

  @Test
  void pagesWithMetadataCanBeSerialized() {
    tester.startPage(QueryPage.class);
    var form = tester.newFormTester("form");
    form.select("filters:conditions:0:namespace", 0);
    form.submit("filters:conditions:0:addMeta");
    var page = tester.getLastRenderedPage();
    var serializer =
        new org.apache.wicket.serialize.java.JavaSerializer(
            tester.getApplication().getApplicationKey());
    assertThat(serializer.serialize(page)).isNotNull();
    tester.startPage(ReportPage.class);
    assertThat(serializer.serialize(tester.getLastRenderedPage())).isNotNull();
    tester.startPage(IdPage.class);
    assertThat(serializer.serialize(tester.getLastRenderedPage())).isNotNull();
  }

  @Test
  void malformedUrlStateDoesNotBreakThePage() {
    tester.startPage(
        QueryPage.class,
        new org.apache.wicket.request.mapper.parameter.PageParameters()
            .set("state", "{\"criteria\":null}"));
    tester.assertRenderedPage(QueryPage.class);
    tester.assertContains("invalid state");
  }

  @Test
  void generatesAndClosesReportStream(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    var output = java.nio.file.Files.createFile(directory.resolve("events.json"));
    var fact = Fact.builder().ns("users").type("UserCreated").build("{\"name\":\"Ada\"}");
    when(plugins.process(any(Fact.class)))
        .thenReturn(
            new JsonViewEntry(
                FactCastJson.toObjectNode("{\"payload\":{\"name\":\"Ada\"}}"),
                new JsonEntryMetaData()));
    when(reports.createBatchUpload(eq("GODMODE"), eq("events.json"), any()))
        .thenAnswer(
            invocation ->
                new FileReportUploadStream(
                    new com.fasterxml.jackson.core.JsonFactory(
                        new com.fasterxml.jackson.databind.ObjectMapper()),
                    output,
                    "events.json",
                    invocation.getArgument(2)));
    when(repository.fetchAndProcessAll(any(), any()))
        .thenAnswer(
            invocation -> {
              java.util.concurrent.CompletableFuture.runAsync(
                      () ->
                          invocation.<java.util.function.Consumer<Fact>>getArgument(1).accept(fact))
                  .join();
              return 1L;
            });
    tester.startPage(ReportPage.class);
    var form = tester.newFormTester("form");
    form.select("filters:conditions:0:namespace", 0);
    form.setValue("name", "events");
    form.submit("generate");
    tester.assertNoErrorMessage();
    tester.assertContains("Report generated: 1 facts");
    var report =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(java.nio.file.Files.readString(output));
    assertThat(report.path("events").size()).isEqualTo(1);
    assertThat(report.path("events").get(0).path("payload").path("name").asText()).isEqualTo("Ada");
  }

  @Test
  void removesPartialReportAfterFailure(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    var output = java.nio.file.Files.createFile(directory.resolve("failed.json"));
    when(reports.createBatchUpload(any(), any(), any()))
        .thenAnswer(
            invocation ->
                new FileReportUploadStream(
                    new com.fasterxml.jackson.core.JsonFactory(
                        new com.fasterxml.jackson.databind.ObjectMapper()),
                    output,
                    "failed.json",
                    invocation.getArgument(2)));
    when(repository.fetchAndProcessAll(any(), any()))
        .thenThrow(new IllegalStateException("stream failed"));
    tester.startPage(ReportPage.class);
    var form = tester.newFormTester("form");
    form.select("filters:conditions:0:namespace", 0);
    form.setValue("name", "failed");
    form.submit("generate");
    tester.assertContains("The operation failed");
    verify(reports).delete("GODMODE", "failed.json");
    assertThat(
            new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(java.nio.file.Files.readString(output))
                .path("events")
                .isArray())
        .isTrue();
  }

  @Test
  void quickFilterReplacesMatchingMetadataAndRunsQueryAgain() {
    var metadata = new JsonEntryMetaData();
    metadata.addHeaderMetaFilterOption("$.meta.region", "region", List.of("west"));
    var entry =
        new JsonViewEntry(
            FactCastJson.toObjectNode(
                "{\"header\":{\"meta\":{\"region\":\"west\"}},\"payload\":{}}"),
            metadata);
    when(plugins.process(anyList())).thenReturn(new JsonViewEntries(List.of(entry)));
    tester.startPage(QueryPage.class);
    var form = tester.newFormTester("form");
    form.select("filters:conditions:0:namespace", 0);
    form.submit("query");
    tester.newFormTester("results:quickForm").submit("options:0:apply");
    verify(repository, times(2)).fetchChunk(any());
    verify(repository, atLeastOnce())
        .fetchChunk(
            argThat(
                b ->
                    b.getCriteria().getFirst().getMeta().stream()
                        .anyMatch(
                            m -> "region".equals(m.getKey()) && "west".equals(m.getValue()))));
  }

  @Test
  void jsonContentAndPluginAnnotationsAreEscaped() {
    var metadata = new JsonEntryMetaData();
    metadata.annotatePayload("$.name", "<img src=x onerror=alert(1)>");
    var entry =
        new JsonViewEntry(
            FactCastJson.toObjectNode(
                "{\"header\":{},\"payload\":{\"name\":\"<script>alert(1)</script>\"}}"),
            metadata);
    when(plugins.process(anyList())).thenReturn(new JsonViewEntries(List.of(entry)));
    tester.startPage(QueryPage.class);
    var form = tester.newFormTester("form");
    form.select("filters:conditions:0:namespace", 0);
    form.submit("query");
    assertThat(tester.getLastResponseAsString())
        .contains("&lt;script&gt;", "&lt;img")
        .doesNotContain("<img src=x", "<script>alert");
  }

  @Test
  void queryStateCannotCloseAnInlineScript() {
    String script = QueryState.script("{\"key\":\"</script><script>alert(1)</script>\"}");
    assertThat(script).doesNotContain("</script>").contains("\\u003c");
  }

  @Test
  void invalidAggregateIdRetainsInputAndFeedback() {
    tester.startPage(QueryPage.class);
    var form = tester.newFormTester("form");
    form.select("filters:conditions:0:namespace", 0);
    form.setValue("filters:conditions:0:aggregateId", "invalid-aggregate-id");
    form.submit("query");
    tester.assertContains("invalid-aggregate-id");
    assertThat(tester.getFeedbackMessages(null)).isNotEmpty();
    verify(repository, never()).fetchChunk(any());
  }

  @Test
  void cachedExportsCannotBeReadAfterSwitchingAccounts() {
    var entry =
        new JsonViewEntry(
            FactCastJson.toObjectNode("{\"private\":\"first-account-only\"}"),
            new JsonEntryMetaData());
    when(plugins.process(anyList())).thenReturn(new JsonViewEntries(List.of(entry)));
    tester.startPage(QueryPage.class);
    var form = tester.newFormTester("form");
    form.select("filters:conditions:0:namespace", 0);
    form.submit("query");
    tester.assertVisible("form:export");
    var bob = new FactCastUser(new FactCastAccount("bob"), "unused");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(bob, null, bob.getAuthorities()));
    tester.clickLink("form:export");
    assertThat(tester.getLastResponseAsString()).doesNotContain("first-account-only");
    tester.assertRenderedPage(QueryPage.class);
    tester.assertInvisible("form:export");
  }
}
