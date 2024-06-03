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
package org.factcast.factus.dynamo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.amazonaws.services.dynamodbv2.AcquireLockOptions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient;
import com.amazonaws.services.dynamodbv2.LockItem;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@ExtendWith(MockitoExtension.class)
class AbstractDynamoManagedProjectionTest {
  @Mock DynamoDbClient dynamoDbClient;
  @Mock DynamoDbEnhancedClient enhancedClient;
  @Mock AmazonDynamoDBLockClient lockClient;
  @Mock DynamoDbTable<DynamoProjectionState> table;

  static final String SCOPED_NAME =
      "org.factcast.factus.dynamo.AbstractDynamoManagedProjectionTest$TestProjection_1";

  @InjectMocks TestProjection uut;

  @BeforeEach
  void setUp() {
    when(enhancedClient.table(
            any(String.class), ArgumentMatchers.<TableSchema<DynamoProjectionState>>any()))
        .thenReturn(table);
    uut = new TestProjection(dynamoDbClient, enhancedClient, lockClient);
  }

  @Nested
  class WhenInspectingClass {
    @Test
    void getProjectionKey() {
      final TestProjection uut = new TestProjection(dynamoDbClient);
      assertThat(uut.projectionKey()).isEqualTo(SCOPED_NAME);
    }
  }

  @Nested
  class WhenOperatingOnState {
    @Captor private ArgumentCaptor<UpdateItemEnhancedRequest<DynamoProjectionState>> updateCaptor;

    @Test
    void returnsFactStreamPositionIfSet() {
      UUID factId = UUID.randomUUID();
      DynamoProjectionState exp =
          DynamoProjectionState.builder().key("key").factStreamPosition(factId).serial(2L).build();
      when(table.getItem(ArgumentMatchers.<DynamoProjectionState>any())).thenReturn(exp);

      FactStreamPosition res = uut.factStreamPosition();

      verify(table).getItem(DynamoProjectionState.builder().key(SCOPED_NAME).build());

      assertThat(res).isEqualTo(FactStreamPosition.of(factId, 2L));
    }

    @Test
    void returnsNullIfFactStreamPositionNotSet() {
      when(table.getItem(ArgumentMatchers.<DynamoProjectionState>any())).thenReturn(null);

      FactStreamPosition res = uut.factStreamPosition();

      verify(table).getItem(DynamoProjectionState.builder().key(SCOPED_NAME).build());

      assertThat(res).isNull();
    }

    @Test
    void settingFactSteamPosition() {
      UUID factId = UUID.randomUUID();
      FactStreamPosition state = FactStreamPosition.of(factId, 2L);

      uut.factStreamPosition(state);

      verify(table).updateItem(updateCaptor.capture());
      final UpdateItemEnhancedRequest<DynamoProjectionState> value = updateCaptor.getValue();
      assertThat(value.item().factStreamPosition()).isEqualTo(factId);
      assertThat(value.item().serial()).isEqualTo(2L);
    }
  }

  @Nested
  class WhenAquiringWriteToken {

    @Captor private ArgumentCaptor<AcquireLockOptions> captor;

    @Test
    @SneakyThrows
    void TokenIsValidAndMaxWaitShorterThanLeaseDuration() {
      Duration maxWaitDuration = Duration.ofSeconds(7);
      LockItem lock = mock(LockItem.class);
      when(lock.isExpired()).thenReturn(false);
      when(lockClient.tryAcquireLock(any())).thenReturn(Optional.of(lock));

      final WriterToken res = uut.acquireWriteToken(maxWaitDuration);

      verify(lockClient).tryAcquireLock(captor.capture());
      assertThat(captor.getValue())
          .isEqualTo(
              AcquireLockOptions.builder(SCOPED_NAME + "_lock")
                  // maxWait is applied on top of leaseDuration period.
                  .withAdditionalTimeToWaitForLock(0L)
                  .withTimeUnit(TimeUnit.SECONDS)
                  .build());

      assertThat(res.isValid()).isTrue();
    }

    @Test
    @SneakyThrows
    void returnsInvalidTokenIfLockIsExpired() {
      Duration maxWaitDuration = Duration.ofSeconds(7);
      LockItem lock = mock(LockItem.class);
      when(lock.isExpired()).thenReturn(true);
      when(lockClient.tryAcquireLock(any())).thenReturn(Optional.of(lock));

      final WriterToken res = uut.acquireWriteToken(maxWaitDuration);

      assertThat(res.isValid()).isFalse();
    }

    @Test
    @SneakyThrows
    void maxWaitExceedsLeaseDuration() {
      Duration maxWaitDuration = Duration.ofSeconds(33);
      LockItem lock = mock(LockItem.class);
      when(lockClient.tryAcquireLock(any())).thenReturn(Optional.of(lock));

      final WriterToken res = uut.acquireWriteToken(maxWaitDuration);

      verify(lockClient).tryAcquireLock(captor.capture());
      assertThat(captor.getValue())
          .isEqualTo(
              AcquireLockOptions.builder(SCOPED_NAME + "_lock")
                  // maxWait is applied on top of leaseDuration period.
                  .withAdditionalTimeToWaitForLock(23L)
                  .withTimeUnit(TimeUnit.SECONDS)
                  .build());
    }

    @Test
    @SneakyThrows
    void returnsNullIfInterrupted() {
      Duration maxWaitDuration = Duration.ofSeconds(33);
      when(lockClient.tryAcquireLock(any())).thenThrow(InterruptedException.class);

      final WriterToken res = uut.acquireWriteToken(maxWaitDuration);

      assertThat(res).isNull();
    }
  }

  @Nested
  class MissingProjectionMetaDataAnnotation {

    @Test
    void happyPath() {
      assertThatThrownBy(() -> new MissingAnnotationTestProjection(dynamoDbClient))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @ProjectionMetaData(revision = 1)
  static class TestProjection extends AbstractDynamoManagedProjection {

    public TestProjection(@NonNull DynamoDbClient dynamoDb) {
      super(dynamoDb, "stateTable");
    }

    public TestProjection(
        @NonNull DynamoDbClient dynamoDb,
        @NonNull DynamoDbEnhancedClient enhancedClient,
        @NonNull AmazonDynamoDBLockClient lockClient) {
      super(dynamoDb, enhancedClient, lockClient, "stateTable");
    }
  }

  static class MissingAnnotationTestProjection extends AbstractDynamoManagedProjection {

    public MissingAnnotationTestProjection(@NonNull DynamoDbClient dynamoDb) {
      super(dynamoDb, "stateTable");
    }
  }
}
