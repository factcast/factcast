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
package org.factcast.factus.spring.tx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.time.Duration;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.projection.WriterToken;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class ASpringTxProjectionTest {

  final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
  final NotAnnotatedSpringTxProjection uutNotAnnotated =
      new NotAnnotatedSpringTxProjection(transactionManager);
  final AnnotatedSpringTxProjection uutAnnotated =
      new AnnotatedSpringTxProjection(transactionManager);
  final UnlimitedBulkSizeSpringTxProjection uutUnlimitedBulkSize =
      new UnlimitedBulkSizeSpringTxProjection(transactionManager);

  @Test
  void delegatesBeginNewTransaction() {
    uutNotAnnotated.beginNewTransaction();
    verify(transactionManager).getTransaction(any());
  }

  @Test
  void delegatesRollback() {
    uutNotAnnotated.rollback(mock(TransactionStatus.class));
    verify(transactionManager).rollback(any());
  }

  @Test
  void delegatesCommit() {
    uutNotAnnotated.commit(mock(TransactionStatus.class));
    verify(transactionManager).commit(any());
  }

  @Test
  void defaultTransactionOptions() {
    assertEquals(SpringTransactional.Defaults.create(), uutNotAnnotated.transactionOptions());
  }

  @Test
  void transactionOptionsOnAnnotatedProjection() {
    SpringTransactional tx = uutAnnotated.getClass().getAnnotation(SpringTransactional.class);
    assertEquals(SpringTransactional.Defaults.with(tx), uutNotAnnotated.transactionOptions());
  }

  @Test
  void defaultMaxBatchSize() {
    assertEquals(10000, uutNotAnnotated.maxBatchSizePerTransaction());
  }

  @Test
  void maxBatchSizeForAnnotatedProjection() {
    assertEquals(42, uutAnnotated.maxBatchSizePerTransaction());
  }

  @Test
  void defaultMaxBatchSizeOnUnlimitedBulkSize() {
    assertEquals(10000, uutUnlimitedBulkSize.maxBatchSizePerTransaction());
  }

  private static class NotAnnotatedSpringTxProjection extends AbstractSpringTxProjection {
    public NotAnnotatedSpringTxProjection(PlatformTransactionManager platformTransactionManager) {
      super(platformTransactionManager);
    }

    @Nullable
    @Override
    public FactStreamPosition factStreamPosition() {
      return null;
    }

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {}

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      return null;
    }
  }

  @SpringTransactional(bulkSize = 42)
  private static class AnnotatedSpringTxProjection extends AbstractSpringTxProjection {
    public AnnotatedSpringTxProjection(PlatformTransactionManager platformTransactionManager) {
      super(platformTransactionManager);
    }

    @Nullable
    @Override
    public FactStreamPosition factStreamPosition() {
      return null;
    }

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {}

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      return null;
    }
  }

  @SpringTransactional(bulkSize = 0)
  private static class UnlimitedBulkSizeSpringTxProjection extends AbstractSpringTxProjection {
    public UnlimitedBulkSizeSpringTxProjection(
        PlatformTransactionManager platformTransactionManager) {
      super(platformTransactionManager);
    }

    @Nullable
    @Override
    public FactStreamPosition factStreamPosition() {
      return null;
    }

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {}

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      return null;
    }
  }
}
