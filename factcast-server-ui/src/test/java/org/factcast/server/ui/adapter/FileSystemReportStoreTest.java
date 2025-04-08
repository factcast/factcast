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
import java.nio.file.*;
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
    @Test
    void createsReportFileAndParentDirectories() {
      uut.save(USER_NAME, getReport("name.json"));
      assertTrue(Files.exists(new File(PERSISTENCE_DIR + "/user/name.json").toPath()));
    }

    @Test
    void throwsIllegalArgumentExceptionIfReportExists() {
      Report report = getReport("name.json");
      uut.save(USER_NAME, report);
      // try again
      assertThrows(IllegalArgumentException.class, () -> uut.save(USER_NAME, report));
    }

    @Test
    @SneakyThrows
    void throwsWrappedIOExceptionIfSavingFails() {
      Report report = getReport("name.json");
      ObjectMapper explodingOm = mock(ObjectMapper.class);
      doThrow(new IOException("foo"))
          .when(explodingOm)
          .writeValue(any(File.class), any(Object.class));
      uut.objectMapper(explodingOm);

      assertThatThrownBy(() -> uut.save(USER_NAME, report))
          .isInstanceOf(RuntimeException.class)
          .hasCauseInstanceOf(IOException.class);
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
      uut.save("user2", getReport("name.json"));

      assertTrue(uut.listAllForUser(USER_NAME).isEmpty());
    }

    @Test
    void listsAllReports() {
      uut.save(USER_NAME, getReport("report1.json"));
      uut.save(USER_NAME, getReport("report2.json"));
      uut.save("user2", getReport("report3.json"));

      final var actual = uut.listAllForUser(USER_NAME);

      assertThat(actual).hasSize(2);
      assertThat(actual)
          .extracting(ReportEntry::name)
          .containsExactly("report1.json", "report2.json");
    }
  }

  @Nested
  class WhenDeletingReports {

    @Test
    @SneakyThrows
    void happyPath() {
      final var reportName = "report.json";
      final var filePath = Paths.get(PERSISTENCE_DIR, USER_NAME, reportName);
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
