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
package org.factcast.server.ui.s3reportstore;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.server.ui.port.ReportStore;
import org.factcast.server.ui.report.*;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
public class S3ReportStore implements ReportStore {

  private final S3AsyncClient s3Client;
  private final S3Presigner s3Presigner;
  private final String bucketName;
  private final ObjectMapper objectMapper;

  public S3ReportStore(
      @NonNull S3AsyncClient s3Client,
      @NonNull S3Presigner s3Presigner,
      @NonNull String bucketName) {
    this.s3Client = s3Client;
    this.s3Presigner = s3Presigner;
    this.bucketName = bucketName;
    final var om = new ObjectMapper();
    om.registerModule(new JavaTimeModule());
    om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.objectMapper = om;
  }

  @Override
  @SneakyThrows
  public S3BatchedReportUploadStream createBatchUpload(
      @NonNull String userName, @NonNull String reportName, @NonNull ReportFilterBean query) {
    final var reportKey = getReportKey(userName, reportName);
    if (doesObjectExist(reportKey)) {
      throw new IllegalArgumentException(
          "Report was not generated as another report with this name already exists.");
    }
    final var queryString = objectMapper.writeValueAsString(query);
    return new S3BatchedReportUploadStream(
        s3Client, bucketName, new JsonFactory(objectMapper), reportKey, reportName, queryString);
  }

  private static String getReportKey(String userName, String reportName) {
    return Paths.get(userName, reportName).toString();
  }

  @Override
  public URL getReportDownload(@NonNull String userName, @NonNull String reportName) {
    String key = getReportKey(userName, reportName);
    checkObjectExists(key);

    final var presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofHours(2))
            .getObjectRequest(get -> get.bucket(bucketName).key(key))
            .build();

    URL url = s3Presigner.presignGetObject(presignRequest).url();
    log.info("Generated pre-signed URL: {}", url.toExternalForm());
    return url;
  }

  @Override
  public List<ReportEntry> listAllForUser(@NonNull String userName) {
    // list all objects in the aws s3 bucket under the prefix userName.
    ListObjectsV2Request listObjectsV2Request =
        ListObjectsV2Request.builder().bucket(bucketName).prefix(userName).build();

    try {
      final var reportEntries =
          s3Client.listObjectsV2(listObjectsV2Request).get().contents().stream()
              .map(
                  o -> new ReportEntry(getReportNameFromKey(o.key()), Date.from(o.lastModified())));

      return reportEntries.toList();
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw ExceptionHelper.toRuntime(ie);
    } catch (ExecutionException ee) {
      log.error("Failed to list reports", ee);
      throw ExceptionHelper.toRuntime(ee);
    }
  }

  @Override
  public void delete(@NonNull String userName, @NonNull String reportName) {
    String reportKey = getReportKey(userName, reportName);
    checkObjectExists(reportKey);

    s3Client
        .deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(reportKey).build())
        .join();
  }

  private void checkObjectExists(String key) {
    try {
      s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build()).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof NoSuchKeyException) {
        throw new ReportDoesNotExistException(key);
      }
      throw e;
    }
  }

  private boolean doesObjectExist(String key) {
    try {
      checkObjectExists(key);
      return true;
    } catch (ReportDoesNotExistException e) {
      return false;
    }
  }

  private String getReportNameFromKey(@NonNull String key) {
    return key.substring(key.lastIndexOf('/') + 1);
  }
}
