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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.vaadin.flow.component.UI;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.server.ui.port.ReportStore;
import org.factcast.server.ui.report.Report;
import org.factcast.server.ui.report.ReportDownload;
import org.factcast.server.ui.report.ReportEntry;
import org.springframework.core.io.InputStreamResource;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static java.io.File.separator;

@Slf4j
public class FileSystemReportStore implements ReportStore {

  @VisibleForTesting static final String PERSISTENCE_DIR = "factcast-ui" + separator + "reports";

  private URL downloadLink;

  @Override
  public void save(@NonNull String userName, @NonNull Report report) {
    final var reportFilePath = Paths.get(PERSISTENCE_DIR, userName, report.name());
    // create resource
    log.info("Saving report to {}", reportFilePath);

    if (!Files.exists(reportFilePath)) {
      final var objectMapper = new ObjectMapper();
      try {
        Files.createDirectories(reportFilePath.getParent());
        log.info("Parent dirs created");
        Files.createFile(reportFilePath);
        log.info("File created");

        objectMapper.writeValue(reportFilePath.toFile(), report);
      } catch (IOException e) {
        log.error("Failed to save report", e);
        throw new RuntimeException(e);
      }
    } else {
      // TODO: clean this up
      throw new IllegalArgumentException(
          "Report was not generated as another report with this name already exists.");
    }
  }

  @SneakyThrows
  @Override
  public ReportDownload getReport(@NonNull String userName, @NonNull String reportName) {
    log.info("Constructing download link to redirect to download of {}", reportName);
    UI.getCurrent()
        .getPage()
        .fetchCurrentURL(
            url -> {
              final var path = url.getPath();
              log.info("Path: {}", path);
              try {
                this.downloadLink = new URL(url, "/downloads/" + reportName);
              } catch (MalformedURLException e) {
                log.error("Failed to construct download link", e);
              }
            });
    return new ReportDownload(downloadLink, reportName);
  }

  @Override
  public InputStreamResource getReportAsStream(
      @NonNull String userName, @NonNull String reportName) {
    return new InputStreamResource(loadReport(userName, reportName));
  }

  private InputStream loadReport(@NonNull String userName, @NonNull String reportName) {
    final var reportFilePath = Paths.get(PERSISTENCE_DIR, userName, reportName);
    log.info("Getting report from {}", reportFilePath);
    if (Files.exists(reportFilePath)) {
      try {
        return Files.newInputStream(reportFilePath);
      } catch (IOException e) {
        log.error("Failed to get report", e);
        throw new RuntimeException(e);
      }
    } else {
      throw new IllegalArgumentException(
          String.format("No report exists with name %s for user %s", reportName, userName));
    }
  }

  @Override
  public List<ReportEntry> listAllForUser(@NonNull String userName) {
    final var reportDir = Paths.get(PERSISTENCE_DIR, userName);
    if (!Files.exists(reportDir)) {
      return List.of();
    }
    try (var stream = Files.list(reportDir)) {
      return stream
          .filter(file -> !Files.isDirectory(file))
          .map(file -> new ReportEntry(file.getFileName().toString(), getLastModified(file)))
          .sorted(Comparator.comparing(ReportEntry::name))
          .toList();
    } catch (IOException e) {
      log.error("Failed to list reports for user {}", userName, e);
      throw new RuntimeException(e);
    }
  }

  static Date getLastModified(Path path) {
    try {
      return Date.from(Files.getLastModifiedTime(path).toInstant());
    } catch (IOException e) {
      log.error("Failed to get last modified time for {}", path, e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void delete(@NonNull String userName, @NonNull String reportName) {
    final var reportFilePath = Paths.get(PERSISTENCE_DIR, userName, reportName);
    log.info("Deleting report: {}", reportFilePath);
    if (Files.exists(reportFilePath)) {
      try {
        Files.delete(reportFilePath);
      } catch (IOException e) {
        log.error("Failed to delete report", e);
        throw new RuntimeException(e);
      }
    } else {
      throw new IllegalArgumentException(
          String.format("No report exists with name %s for user %s", reportName, userName));
    }
  }

  // TODO: maxDiskSpace
}
