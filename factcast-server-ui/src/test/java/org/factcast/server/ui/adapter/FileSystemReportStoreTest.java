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
package org.factcast.server.ui.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.server.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.server.ui.report.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

@Slf4j
class FileSystemReportStoreTest {
  private final FileSystemReportStore uut = new FileSystemReportStore(PERSISTENCE_DIR);

  private static final String PERSISTENCE_DIR = "factcast-ui/reports";
  private static final String USER_NAME = "user";
  private static final String REPORT_NAME = "report.json";
  final ReportFilterBean queryBean = new ReportFilterBean(1);

  @AfterEach
  @SneakyThrows
  void cleanup() {
    log.info("Cleaning up.");
    Path reportPath = Path.of(PERSISTENCE_DIR);
    if (Files.exists(reportPath)) {
      try (Stream<Path> paths = Files.walk(reportPath)) {
        paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      }
    }
  }

  @Nested
  class WhenSavingReports {
    final ReportFilterBean queryBean = new ReportFilterBean(1);

    @Test
    void createsReportFileAndParentDirectories() {
      final var upload = uut.createBatchUpload(USER_NAME, REPORT_NAME, queryBean);
      upload.close();
      assertTrue(Files.exists(Path.of(PERSISTENCE_DIR, "user", REPORT_NAME)));
    }

    @Test
    void throwsIllegalArgumentExceptionIfReportExists() {
      final var upload = uut.createBatchUpload(USER_NAME, REPORT_NAME, queryBean);
      upload.close();
      // try again
      assertThrows(
          IllegalArgumentException.class,
          () -> uut.createBatchUpload(USER_NAME, REPORT_NAME, queryBean));
    }
  }

  @Nested
  class WhenListingReports {
    @Test
    void returnsEmptyListIfNoReportsExist() {
      assertTrue(uut.listAllForUser(USER_NAME).isEmpty());
    }

    @Test
    void returnsEmptyListIfNoReportsExistForUser() {
      uut.createBatchUpload("user2", REPORT_NAME, queryBean);

      assertTrue(uut.listAllForUser(USER_NAME).isEmpty());
    }

    @Test
    void listsAllReports() {
      uut.createBatchUpload(USER_NAME, REPORT_NAME, queryBean);
      uut.createBatchUpload(USER_NAME, "report2.json", queryBean);
      uut.createBatchUpload("user2", "report3.json", queryBean);

      final var actual = uut.listAllForUser(USER_NAME);

      assertThat(actual).hasSize(2);
      assertThat(actual)
          .extracting(ReportEntry::name)
          .containsExactly("report.json", "report2.json");
    }
  }

  @Nested
  class WhenDeletingReports {

    @Test
    @SneakyThrows
    void happyPath() {
      final var reportName = "report.json";
      final var filePath = Path.of(PERSISTENCE_DIR, USER_NAME, reportName);
      createFile(filePath);

      assertThatCode(() -> uut.delete(USER_NAME, "report.json")).doesNotThrowAnyException();
      assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    @SneakyThrows
    void throwsIfFileDoesNotExist() {
      assertThatThrownBy(() -> uut.delete(USER_NAME, "report.json"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("No report exists with name report.json for user user");
    }

    @Test
    @SneakyThrows
    void throwsWrappedIOExceptionIfDeletingFails() {
      try (MockedStatic<Files> files = mockStatic(Files.class)) {
        files.when(() -> Files.exists(any())).thenReturn(true);
        files.when(() -> Files.delete(any())).thenThrow(new IOException("foo"));

        assertThatThrownBy(() -> uut.delete(USER_NAME, "report.json"))
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(IOException.class);
      }
    }

    @SneakyThrows
    private void createFile(Path path) {
      Files.createDirectories(path.getParent());
      Files.createFile(path);
    }
  }

  @Nested
  class WhenGettingReportUrls {

    @Test
    void urlIsCorrect() {
      // ARRANGE
      final var reportName = "report.json";
      final var vaadinServletRequest = mock(VaadinServletRequest.class);
      final var httpServletRequest = mock(HttpServletRequest.class);

      try (MockedStatic<VaadinService> vaadin = mockStatic(VaadinService.class)) {
        vaadin.when(VaadinService::getCurrentRequest).thenReturn(vaadinServletRequest);
        when(vaadinServletRequest.getHttpServletRequest()).thenReturn(httpServletRequest);
        when(httpServletRequest.getRequestURL())
            .thenReturn(new StringBuffer("http://localhost:8080/"));
        // ACT
        final var actualUrl = uut.getReportDownload(USER_NAME, reportName);
        // ASSERT
        assertThat(actualUrl).hasToString("http://localhost:8080/files/" + reportName);
      }
    }
  }

  private static @NotNull Report getReport(String fileName) {
    final var om = new ObjectMapper();
    ObjectNode event = om.getNodeFactory().objectNode();
    event.put("foo", "bar");
    return new Report(fileName, List.of(event), new ReportFilterBean(1), OffsetDateTime.now());
  }
}
