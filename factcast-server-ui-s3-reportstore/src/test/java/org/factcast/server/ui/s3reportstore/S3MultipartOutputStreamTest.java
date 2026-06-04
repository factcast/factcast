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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

@ExtendWith(MockitoExtension.class)
class S3MultipartOutputStreamTest {
  public static final int MB = 1024 * 1024;

  @Mock S3AsyncClient s3;

  S3MultipartOutputStream uut;

  @Nested
  class WhenCreatingStream {
    @Test
    void happyPath() {
      final var captor = ArgumentCaptor.forClass(CreateMultipartUploadRequest.class);

      mockCreateMultipartUploadResponse();
      assertThatCode(() -> new S3MultipartOutputStream(s3, "bucket", "key", 5 * MB))
          .doesNotThrowAnyException();

      verify(s3).createMultipartUpload(captor.capture());

      CreateMultipartUploadRequest request = captor.getValue();
      assertThat(request.bucket()).isEqualTo("bucket");
      assertThat(request.key()).isEqualTo("key");
      assertThat(request.checksumAlgorithm()).isEqualTo(ChecksumAlgorithm.CRC32);
      assertThat(request.contentType()).isEqualTo("application/json; charset=utf-8");
    }

    @Test
    void throwsIfPartSizeBelow5Mb() {
      assertThatThrownBy(() -> new S3MultipartOutputStream(s3, "bucket", "key", 1024))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class WhenWritingData {
    @BeforeEach
    @SneakyThrows
    void setup() {
      mockCreateMultipartUploadResponse();
      uut = new S3MultipartOutputStream(s3, "bucket", "key", 5 * MB);
    }

    @Test
    void doesNotUploadBeforePartSizeReached() {
      byte[] data = new byte[MB]; // 1 MB
      assertThatCode(() -> uut.write(data, 0, data.length)).doesNotThrowAnyException();

      verify(s3).createMultipartUpload(any(CreateMultipartUploadRequest.class));
      verifyNoMoreInteractions(s3);
    }

    @Test
    void writeDataInOnePart() {
      final var uploadCaptor = ArgumentCaptor.forClass(UploadPartRequest.class);
      final var completeCaptor = ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
      mockUploadPartResponse();
      mockCompleteUploadResponse();

      byte[] data = new byte[MB]; // 1 MB
      assertThatCode(
              () -> {
                uut.write(data, 0, data.length);
                uut.close();
              })
          .doesNotThrowAnyException();

      verify(s3).createMultipartUpload(any(CreateMultipartUploadRequest.class));
      verify(s3).uploadPart(uploadCaptor.capture(), any(AsyncRequestBody.class));
      verify(s3).completeMultipartUpload(completeCaptor.capture());
      verifyNoMoreInteractions(s3);

      final var uploadRequests = uploadCaptor.getValue();
      assertThat(uploadRequests.bucket()).isEqualTo("bucket");
      assertThat(uploadRequests.key()).isEqualTo("key");
      assertThat(uploadRequests.uploadId()).isEqualTo("u1");
      assertThat(uploadRequests.partNumber()).isEqualTo(1);

      CompleteMultipartUploadRequest completeRequest = completeCaptor.getValue();
      assertThat(completeRequest.bucket()).isEqualTo("bucket");
      assertThat(completeRequest.key()).isEqualTo("key");
      assertThat(completeRequest.uploadId()).isEqualTo("u1");
      assertThat(completeRequest.multipartUpload().parts()).hasSize(1);
    }

    @Test
    void writeDataInMultipleParts() {
      final var uploadCaptor = ArgumentCaptor.forClass(UploadPartRequest.class);
      final var completeCaptor = ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
      mockUploadPartResponse();
      mockCompleteUploadResponse();

      byte[] data = new byte[11 * MB]; // 6 MB
      assertThatCode(
              () -> {
                uut.write(data, 0, data.length);
                uut.close();
              })
          .doesNotThrowAnyException();

      verify(s3, times(3)).uploadPart(uploadCaptor.capture(), any(AsyncRequestBody.class));
      verify(s3).completeMultipartUpload(completeCaptor.capture());

      final var uploadRequests = uploadCaptor.getAllValues();
      assertThat(uploadRequests).hasSize(3);
      final var firstPartUpload = uploadRequests.getFirst();
      assertThat(firstPartUpload.bucket()).isEqualTo("bucket");
      assertThat(firstPartUpload.key()).isEqualTo("key");
      assertThat(firstPartUpload.uploadId()).isEqualTo("u1");
      assertThat(firstPartUpload.partNumber()).isEqualTo(1);

      final var secondPartUpload = uploadRequests.get(1);
      assertThat(secondPartUpload.uploadId()).isEqualTo("u1");
      assertThat(secondPartUpload.partNumber()).isEqualTo(2);

      CompleteMultipartUploadRequest completeRequest = completeCaptor.getValue();
      assertThat(completeRequest.bucket()).isEqualTo("bucket");
      assertThat(completeRequest.key()).isEqualTo("key");
      assertThat(completeRequest.uploadId()).isEqualTo("u1");
      assertThat(completeRequest.multipartUpload().parts()).hasSize(3);
    }

    @Test
    void abortUploadOnFailure() {
      final var abortUploadCaptor = ArgumentCaptor.forClass(AbortMultipartUploadRequest.class);
      final var exception = SdkClientException.create("failed");
      when(s3.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
          .thenThrow(exception);

      byte[] data = new byte[6 * MB];
      assertThatThrownBy(() -> uut.write(data, 0, data.length))
          .isInstanceOf(exception.getClass())
          .hasMessage(exception.getMessage());

      verify(s3).abortMultipartUpload(abortUploadCaptor.capture());

      final var abortRequest = abortUploadCaptor.getValue();
      assertThat(abortRequest.bucket()).isEqualTo("bucket");
      assertThat(abortRequest.key()).isEqualTo("key");
      assertThat(abortRequest.uploadId()).isEqualTo("u1");
    }
  }

  @Nested
  class WhenClosingStream {
    @BeforeEach
    @SneakyThrows
    void setup() {
      mockCreateMultipartUploadResponse();
      uut = new S3MultipartOutputStream(s3, "bucket", "key", 5 * MB);
    }

    @Test
    void doesCompleteUpload() {
      mockCompleteUploadResponse();
      assertThatCode(() -> uut.close()).doesNotThrowAnyException();

      verify(s3).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
      verifyNoMoreInteractions(s3);
    }

    @Test
    void uploadsRemainingBufferBeforeClosing() {
      final var uploadCaptor = ArgumentCaptor.forClass(UploadPartRequest.class);
      final var completeCaptor = ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
      mockUploadPartResponse();
      mockCompleteUploadResponse();

      byte[] data = new byte[MB]; // 6 MB
      assertThatCode(
              () -> {
                uut.write(data, 0, data.length);
                uut.close();
              })
          .doesNotThrowAnyException();

      verify(s3).uploadPart(uploadCaptor.capture(), any(AsyncRequestBody.class));
      verify(s3).completeMultipartUpload(completeCaptor.capture());

      final var firstPartUpload = uploadCaptor.getValue();
      assertThat(firstPartUpload.bucket()).isEqualTo("bucket");
      assertThat(firstPartUpload.key()).isEqualTo("key");
      assertThat(firstPartUpload.uploadId()).isEqualTo("u1");
      assertThat(firstPartUpload.partNumber()).isEqualTo(1);

      CompleteMultipartUploadRequest completeRequest = completeCaptor.getValue();
      assertThat(completeRequest.bucket()).isEqualTo("bucket");
      assertThat(completeRequest.key()).isEqualTo("key");
      assertThat(completeRequest.uploadId()).isEqualTo("u1");
      assertThat(completeRequest.multipartUpload().parts()).hasSize(1);
    }

    @Test
    void onError_throwsIOException() {
      when(s3.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
          .thenThrow(SdkClientException.create("test"));

      assertThatThrownBy(() -> uut.close()).isInstanceOf(IOException.class);
    }
  }

  @Test
  @SneakyThrows
  void doNothingOnFlush() {
    mockCreateMultipartUploadResponse();
    uut = new S3MultipartOutputStream(s3, "bucket", "key", 5 * MB);

    assertThatCode(uut::flush).doesNotThrowAnyException();
  }

  private void mockCreateMultipartUploadResponse() {
    when(s3.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                CreateMultipartUploadResponse.builder().uploadId("u1").build()));
  }

  private void mockUploadPartResponse() {
    when(s3.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                UploadPartResponse.builder().checksumCRC32("checksum").eTag("etag1").build()));
  }

  private void mockCompleteUploadResponse() {
    when(s3.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(CompleteMultipartUploadResponse.builder().build()));
  }
}
