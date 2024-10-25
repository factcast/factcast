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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import javax.annotation.*;
import lombok.*;
import org.factcast.core.*;
import org.factcast.factus.projection.*;
import org.factcast.factus.projection.tx.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.springframework.transaction.*;

@ExtendWith(MockitoExtension.class)
class AbstractSpringTxProjectionTest {

  static final FactStreamPosition FSP = FactStreamPosition.of(UUID.randomUUID(), -1);

  @Mock PlatformTransactionManager platformTransactionManager;

  @Mock TransactionStatus transactionStatus;

  @Spy @InjectMocks MySpringTxProjection underTest;

  @Test
  void writeSPInTx() {
    when(platformTransactionManager.getTransaction(any())).thenReturn(transactionStatus);

    assertThatThrownBy(() -> underTest.transactionalFactStreamPosition(FSP))
        .isInstanceOf(TransactionNotRunningException.class);

    underTest.begin();

    underTest.transactionalFactStreamPosition(FSP);

    verify(underTest).factStreamPosition(FSP);
  }

  static class MySpringTxProjection extends AbstractSpringTxProjection {
    protected MySpringTxProjection(PlatformTransactionManager platformTransactionManager) {
      super(platformTransactionManager);
    }

    @Nullable
    @Override
    public FactStreamPosition factStreamPosition() {
      return FSP;
    }

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {
      // intentionally empty
    }

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      return null;
    }
  }
}
