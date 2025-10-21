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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vaadin.flow.server.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.server.ui.port.FileBatchedReportUploadStream;
import org.factcast.server.ui.port.ReportStore;
import org.factcast.server.ui.report.*;

@Slf4j
public class FileSystemReportStore implements ReportStore {

  public final String persistenceDir;

  @Setter(value = AccessLevel.PACKAGE)
  private ObjectMapper objectMapper;

  public FileSystemReportStore(@NonNull String persistenceDir) {
    this.persistenceDir = persistenceDir;
    final var om = new ObjectMapper();
    om.registerModule(new JavaTimeModule());
    om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.objectMapper = om;
  }

  @Override
  public FileBatchedReportUploadStream createBatchUpload(
      @NonNull String userName, @NonNull String reportName, @NonNull ReportFilterBean query) {
    final var reportFilePath = Paths.get(persistenceDir, userName, reportName);
    log.info("Saving report to {}", reportFilePath);
    log.info("Usable space in partition: {} MB", getUsableSpaceInMb(persistenceDir));

    if (!Files.exists(reportFilePath)) {
      try {
        Files.createDirectories(reportFilePath.getParent());
        log.info("Parent dirs created");
        final var path = Files.createFile(reportFilePath);
        log.info("File created");
        final var queryString = objectMapper.writeValueAsString(query);
        return new FileBatchedReportUploadStream(path, reportName, queryString);
      } catch (IOException e) {
        log.error("Failed to save report", e);
        throw ExceptionHelper.toRuntime(e);
      }
    } else {
      throw new IllegalArgumentException(
          "Report was not generated as another report with this name already exists.");
    }
  }

  @SneakyThrows
  @Override
  public @NonNull URL getReportDownload(@NonNull String userName, @NonNull String reportName) {
    log.info("Constructing download link to redirect to download of {}", reportName);
    String downloadLink = "files/" + reportName;
    return URI.create(getApplicationBaseUrl() + downloadLink).toURL();
  }

  @Override
  public List<ReportEntry> listAllForUser(@NonNull String userName) {
    final var reportDir = Paths.get(persistenceDir, userName);
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
      throw ExceptionHelper.toRuntime(e);
    }
  }

  private static @NonNull Date getLastModified(@NonNull Path path) {
    try {
      return Date.from(Files.getLastModifiedTime(path).toInstant());
    } catch (IOException e) {
      log.error("Failed to get last modified time for {}", path, e);
      throw ExceptionHelper.toRuntime(e);
    }
  }

  @Override
  public void delete(@NonNull String userName, @NonNull String reportName) {
    final var reportFilePath = Paths.get(persistenceDir, userName, reportName);
    log.info("Deleting report: {}", reportFilePath);
    if (Files.exists(reportFilePath)) {
      try {
        Files.delete(reportFilePath);
      } catch (IOException e) {
        log.error("Failed to delete report", e);
        throw ExceptionHelper.toRuntime(e);
      }
    } else {
      throw new IllegalArgumentException(
          String.format("No report exists with name %s for user %s", reportName, userName));
    }
  }

  private static @NonNull String getApplicationBaseUrl() {
    VaadinRequest vaadinRequest = VaadinService.getCurrentRequest();
    HttpServletRequest httpServletRequest =
        ((VaadinServletRequest) vaadinRequest).getHttpServletRequest();
    final var baseUrl = httpServletRequest.getRequestURL().toString();
    log.info("Application base url is: {} ", baseUrl);

    return baseUrl;
  }

  private static long getUsableSpaceInMb(@NonNull String pathInPartition) {
    // on some file systems, partitions have reserved blocks, would be included in return value of
    // getFreeSpace()
    return new File(pathInPartition).getUsableSpace() / (1024 * 1024);
  }
}
