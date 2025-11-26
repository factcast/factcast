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

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

// consider refactoring with
// https://docs.aws.amazon.com/AmazonS3/latest/API/s3_example_s3_Scenario_UploadStream_section.html
@Slf4j
public class S3MultipartOutputStream extends OutputStream {
  private final S3AsyncClient s3;
  private final String bucket, key;
  private final byte[] buf;
  private int count = 0;
  private final List<CompletedPart> parts = new ArrayList<>();
  private final String uploadId;
  private boolean closed = false;

  @SneakyThrows
  public S3MultipartOutputStream(
      @NonNull S3AsyncClient s3, @NonNull String bucket, @NonNull String key, int partSizeBytes) {
    if (partSizeBytes < 5 * 1024 * 1024)
      throw new IllegalArgumentException("partSize must be higher than 5 MiB");
    this.s3 = s3;
    this.bucket = bucket;
    this.key = key;
    this.buf = new byte[partSizeBytes];

    final var multipartUpload =
        s3.createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/json; charset=utf-8")
                .checksumAlgorithm(ChecksumAlgorithm.CRC32)
                .build());
    final var init = multipartUpload.get();
    this.uploadId = init.uploadId();
  }

  @Override
  public void write(int b) throws IOException {
    ensureOpen();
    if (count == buf.length) flushPart();
    buf[count++] = (byte) b;
  }

  @Override
  public void write(byte @NotNull [] b, int off, int len) throws IOException {
    ensureOpen();
    while (len > 0) {
      int space = buf.length - count;
      if (space == 0) {
        flushPart();
        space = buf.length;
      }
      int toCopy = Math.min(space, len);
      System.arraycopy(b, off, buf, count, toCopy);
      count += toCopy;
      off += toCopy;
      len -= toCopy;
      if (count == buf.length) flushPart();
    }
  }

  @Override
  public void flush() {
    // no-op: we only send full parts; final partial is sent on close()
  }

  @Override
  public void close() throws IOException {
    if (closed) return;
    closed = true;
    try {
      // send final (possibly < 5 MiB) part
      if (count > 0) upload(buf, count);
      parts.sort(Comparator.comparingInt(CompletedPart::partNumber));
      s3.completeMultipartUpload(
              CompleteMultipartUploadRequest.builder()
                  .bucket(bucket)
                  .key(key)
                  .uploadId(uploadId)
                  .multipartUpload(mpu -> mpu.parts(parts))
                  .build())
          .get();
    } catch (Exception e) {
      log.error("Error while completing multipart upload: ", e);
      abortQuietly();
      Thread.currentThread().interrupt();
      throw new IOException("Completing multipart upload failed", e);
    }
    log.debug("Completed multipart upload");
  }

  private void flushPart() {
    upload(buf, buf.length);
    count = 0;
  }

  @SneakyThrows
  private void upload(byte[] bytes, int len) {
    try {
      final var partNumber = nextPartNumber();
      final var upload =
          s3.uploadPart(
              UploadPartRequest.builder()
                  .bucket(bucket)
                  .key(key)
                  .uploadId(uploadId)
                  .partNumber(partNumber)
                  .contentLength((long) len)
                  .build(),
              AsyncRequestBody.fromBytes(
                  len == bytes.length ? bytes : java.util.Arrays.copyOfRange(bytes, 0, len)));

      final var partResponse = upload.get();
      parts.add(
          CompletedPart.builder()
              .partNumber(partNumber)
              .checksumCRC32(partResponse.checksumCRC32())
              .eTag(partResponse.eTag())
              .build());
    } catch (Exception e) {
      log.error("Error while uploading multipart upload. Will abort. Error: ", e);
      abortQuietly();
      throw e;
    }
  }

  private void abortQuietly() {
    try {
      s3.abortMultipartUpload(
          AbortMultipartUploadRequest.builder().bucket(bucket).key(key).uploadId(uploadId).build());
    } catch (Exception ignore) {
      // Nothing we can do here.
    }
  }

  private void ensureOpen() throws IOException {
    if (closed) throw new IOException("Stream closed");
  }

  private int nextPartNumber() {
    return parts.size() + 1;
  }
}
