/*
 * Copyright © 2017-2025 factcast.org
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
import lombok.NonNull;
import org.factcast.server.ui.port.ReportUploadStream;
import software.amazon.awssdk.services.s3.S3AsyncClient;

/** Batches received facts until BUFFER_SIZE is reached and uploads it using MultiPartUpload */
public class S3BatchedReportUploadStream extends ReportUploadStream {

  private static final int BUFFER_SIZE = 10 * 1024 * 1024; // 5 MB

  public S3BatchedReportUploadStream(
      @NonNull S3AsyncClient s3Client,
      @NonNull String bucketName,
      @NonNull JsonFactory jsonFactory,
      @NonNull String reportKey,
      @NonNull String reportName,
      @NonNull String queryString) {
    super(
        jsonFactory,
        reportName,
        queryString,
        new S3MultipartOutputStream(s3Client, bucketName, reportKey, BUFFER_SIZE));
  }
}
