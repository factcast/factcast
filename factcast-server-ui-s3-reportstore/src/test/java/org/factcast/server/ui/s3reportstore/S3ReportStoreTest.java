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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URL;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.factcast.server.ui.report.Report;
import org.factcast.server.ui.report.ReportFilterBean;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.transfer.s3.model.CompletedUpload;
import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

@ExtendWith(MockitoExtension.class)
class S3ReportStoreTest {

  @Mock private S3AsyncClient s3Client;
  @Mock private S3Presigner s3Presigner;

  private static final String BUCKET_NAME = "factcast-reports";

  S3ReportStore uut;

  @BeforeEach
  void setup() {
    uut = new S3ReportStore(s3Client, s3Presigner, BUCKET_NAME);
  }

  @Nested
  class WhenSavingReports {

    @Test
    @SneakyThrows
    void happyPath() {
      // given
      final var report = getReport("name.events");
      final var uploadArgumentCaptor = ArgumentCaptor.forClass(UploadRequest.class);

      final var fileUpload = mock(Upload.class);
      final var completedUpload = mock(CompletedUpload.class);
      when(s3TransferManager.upload(any(UploadRequest.class))).thenReturn(fileUpload);
      when(fileUpload.completionFuture())
          .thenReturn(CompletableFuture.completedFuture(completedUpload));
      objectDoesNotExist();

      // when
      assertThatCode(() -> uut.save("user", report)).doesNotThrowAnyException();

      // then
      verify(s3TransferManager).upload(uploadArgumentCaptor.capture());

      assertThat(uploadArgumentCaptor.getValue().putObjectRequest().bucket())
          .isEqualTo(BUCKET_NAME);
      assertThat(uploadArgumentCaptor.getValue().putObjectRequest().key())
          .isEqualTo("user/name.events");
    }

    @Test
    @SneakyThrows
    void reportNameAlreadyExists() {
      // given
      final var report = getReport("name.events");
      objectExists();

      // expect
      assertThatThrownBy(() -> uut.save("user", report))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Report was not generated as another report with this name already exists.");
      verifyNoInteractions(s3TransferManager);
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
      final var presignResponse = mock(PresignedGetObjectRequest.class);
      when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
          .thenReturn(presignResponse);
      when(presignResponse.url()).thenReturn(new URL("http://example.com"));
      objectExists();

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
      objectDoesNotExist();

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
    private final ArgumentCaptor<ListObjectsV2Request> captor =
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
      objectExists();
      when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(null));

      uut.delete("user", "report");

      verify(s3Client).deleteObject(captor.capture());
      final var actualRequest = captor.getValue();

      assertThat(actualRequest.bucket()).isEqualTo("factcast-reports");
      assertThat(actualRequest.key()).isEqualTo("user/report");
    }
  }

  private static @NotNull Report getReport(String fileName) {
    final var om = new ObjectMapper();
    ObjectNode event = om.getNodeFactory().objectNode();
    event.put("foo", "bar");
    return new Report(fileName, List.of(event), new ReportFilterBean(1), OffsetDateTime.now());
  }

  private void objectExists() {
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  private void objectDoesNotExist() {
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(NoSuchKeyException.builder().build()));
  }
}
