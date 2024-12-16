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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.URL;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.factcast.server.ui.report.Report;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedUpload;
import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

@ExtendWith(MockitoExtension.class)
class S3ReportStoreTest {

  @Mock private S3AsyncClient s3Client;
  @Mock private S3Presigner s3Presigner;
  @Mock private S3TransferManager s3TransferManager;

  @InjectMocks S3ReportStore uut;

  private static final String BUCKET_NAME = "factcast-reports";

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(uut, "bucketName", "factcast-reports");
  }

  @Nested
  class WhenSavingReports {

    @Test
    @SneakyThrows
    void happyPath() {
      // given
      final var report = new Report("name.json", "events", "query");
      final var objectMapper = new ObjectMapper();
      final var uploadRequest =
          UploadRequest.builder()
              .putObjectRequest(
                  PutObjectRequest.builder().bucket(BUCKET_NAME).key("user/name.json").build())
              .requestBody(AsyncRequestBody.fromBytes(objectMapper.writeValueAsBytes(report)))
              .build();
      final var fileUpload = mock(Upload.class);
      when(s3TransferManager.upload(uploadRequest)).thenReturn(fileUpload);
      final var completedUpload = mock(CompletedUpload.class);
      when(fileUpload.completionFuture())
          .thenReturn(CompletableFuture.completedFuture(completedUpload));

      // when
      uut.save("user", report);

      // then
      verify(s3TransferManager).upload(uploadRequest);
    }
  }

  @Nested
  class WhenGettingReports {
    final ArgumentCaptor<HeadObjectRequest> headCaptor =
        ArgumentCaptor.forClass(HeadObjectRequest.class);
    final ArgumentCaptor<GetObjectPresignRequest> getCaptor =
        ArgumentCaptor.forClass(GetObjectPresignRequest.class);

    @Test
    @SneakyThrows
    void happyPath() {
      // given
      when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(null);
      final var presignResponse = mock(PresignedGetObjectRequest.class);
      when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
          .thenReturn(presignResponse);
      when(presignResponse.url()).thenReturn(new URL("http://example.com"));

      // when
      uut.getReportDownload("user", "report");

      // then
      verify(s3Client).headObject(headCaptor.capture());
      final var actualHeadRequest = headCaptor.getValue();
      assertThat(actualHeadRequest.bucket()).isEqualTo(BUCKET_NAME);
      assertThat(actualHeadRequest.key()).isEqualTo("user/report");

      verify(s3Presigner).presignGetObject(getCaptor.capture());
      final var actualPresignRequest = getCaptor.getValue();

      assertThat(actualPresignRequest.getObjectRequest().bucket()).isEqualTo(BUCKET_NAME);
      assertThat(actualPresignRequest.getObjectRequest().key()).isEqualTo("user/report");
      assertThat(actualPresignRequest.signatureDuration()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    @SneakyThrows
    void throwsIfObjectDoesNotExist() {
      // given
      when(s3Client.headObject(any(HeadObjectRequest.class)))
          .thenThrow(NoSuchKeyException.builder().build());

      // expect
      assertThatThrownBy(() -> uut.getReportDownload("user", "report"))
          .isInstanceOf(ReportDoesNotExistException.class)
          .hasMessage("Report with id user/report doesn't exist");

      verify(s3Client).headObject(headCaptor.capture());
      final var actualHeadRequest = headCaptor.getValue();
      assertThat(actualHeadRequest.bucket()).isEqualTo(BUCKET_NAME);
      assertThat(actualHeadRequest.key()).isEqualTo("user/report");

      verifyNoInteractions(s3Presigner);
    }
  }

  @Nested
  class WhenListingReports {
    ArgumentCaptor<ListObjectsV2Request> captor =
        ArgumentCaptor.forClass(ListObjectsV2Request.class);

    @Test
    @SneakyThrows
    void happyPath() {
      // given
      final var response = mock(ListObjectsV2Response.class);
      when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
          .thenReturn(CompletableFuture.completedFuture(response));
      final var changedDate1 = ZonedDateTime.now().toInstant();
      final var changedDate2 = ZonedDateTime.now().minusDays(1).toInstant();
      when(response.contents())
          .thenReturn(
              List.of(
                  S3Object.builder().key("user/report1").lastModified(changedDate1).build(),
                  S3Object.builder().key("user/report2").lastModified(changedDate2).build()));

      // when
      final var actual = uut.listAllForUser("user");

      // then
      assertThat(actual).hasSize(2);
      assertThat(actual.get(0).name()).isEqualTo("report1");
      assertThat(actual.get(0).lastChanged()).isEqualTo(Date.from(changedDate1));
      assertThat(actual.get(1).name()).isEqualTo("report2");
      assertThat(actual.get(1).lastChanged()).isEqualTo(Date.from(changedDate2));

      verify(s3Client).listObjectsV2(captor.capture());
      final var actualRequest = captor.getValue();
      assertThat(actualRequest.bucket()).isEqualTo(BUCKET_NAME);
      assertThat(actualRequest.prefix()).isEqualTo("user");
    }

    @Test
    void noReportsExist() {
      // given
      final var response = mock(ListObjectsV2Response.class);
      when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
          .thenReturn(CompletableFuture.completedFuture(response));
      when(response.contents()).thenReturn(Collections.emptyList());

      // when
      final var actual = uut.listAllForUser("user");

      // then
      assertThat(actual).isEmpty();

      verify(s3Client).listObjectsV2(captor.capture());
      final var actualRequest = captor.getValue();
      assertThat(actualRequest.bucket()).isEqualTo(BUCKET_NAME);
      assertThat(actualRequest.prefix()).isEqualTo("user");
    }

    @Test
    void throwsIfBucketDoesNotExist() {
      // given
      when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
          .thenReturn(CompletableFuture.failedFuture(NoSuchBucketException.builder().build()));

      // when
      assertThatThrownBy(() -> uut.listAllForUser("user")).isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  class WhenDeletingReports {

    @Test
    void happyPath() {
      ArgumentCaptor<DeleteObjectRequest> captor =
          ArgumentCaptor.forClass(DeleteObjectRequest.class);

      uut.delete("user", "report");

      verify(s3Client).deleteObject(captor.capture());
      final var actualRequest = captor.getValue();

      assertThat(actualRequest.bucket()).isEqualTo("factcast-reports");
      assertThat(actualRequest.key()).isEqualTo("user/report");
    }
  }
}
