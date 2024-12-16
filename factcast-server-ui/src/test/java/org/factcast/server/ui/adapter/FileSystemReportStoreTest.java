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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.server.ui.report.Report;
import org.factcast.server.ui.report.ReportEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@Slf4j
class FileSystemReportStoreTest {
  private FileSystemReportStore uut = new FileSystemReportStore();

  @AfterEach
  @SneakyThrows
  void setup() {
    log.info("Cleaning up.");
    Path reportPath = Path.of(FileSystemReportStore.PERSISTENCE_DIR);
    if (Files.exists(reportPath)) {
      try (Stream<Path> paths = Files.walk(reportPath)) {
        paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      }
    }
  }

  @Test
  void save() {
    uut.save("user", new Report("name.json", "events", "query"));
    assertTrue(
        Files.exists(new File(FileSystemReportStore.PERSISTENCE_DIR + "/user/name.json").toPath()));
  }

  @Nested
  class WhenListingReports {
    @Test
    void returnsEmptyListIfNoReportsExist() {
      assertTrue(uut.listAllForUser("user").isEmpty());
    }

    @Test
    void returnsEmptyListIfNoReportsExistForUser() {
      uut.save("user2", new Report("report3.json", "events", "query"));

      assertTrue(uut.listAllForUser("user").isEmpty());
    }

    @Test
    void listsAllReports() {
      uut.save("user", new Report("report1.json", "events", "query"));
      uut.save("user", new Report("report2.json", "events", "query"));
      uut.save("user2", new Report("report3.json", "events", "query"));

      final var actual = uut.listAllForUser("user");

      assertThat(actual.size()).isEqualTo(2);
      assertThat(actual)
          .extracting(ReportEntry::name)
          .containsExactly("report1.json", "report2.json");
    }
  }

  //  @Nested
  //  class WhenGettingReports {
  //
  //    @Test
  //    @SneakyThrows
  //    void happyPath() {
  //      String reportName = "report1.json";
  //      final var objectMapper = new ObjectMapper();
  //      uut.save("user", new Report(reportName, "events", "query"));
  //
  //      final var reportStream = uut.getReportAsStream("user", reportName);
  //      final var actual = reportStream.getInputStream().readAllBytes();
  //
  //      final var mappedReport = objectMapper.readValue(actual, Report.class);
  //      assertThat(mappedReport.name()).isEqualTo(reportName);
  //    }
  //  }
}
