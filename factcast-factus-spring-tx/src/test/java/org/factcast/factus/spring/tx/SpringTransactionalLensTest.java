/*
 * Copyright © 2017-2022 factcast.org
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.function.*;
import org.factcast.core.Fact;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class SpringTransactionalLensTest {

  @Mock private PlatformTransactionManager transactionManager;

  @Mock private org.factcast.factus.spring.tx.SpringTxManager springTxManager;

  @Mock private TransactionDefinition definition;

  @Mock private Fact f;

  @Nested
  class Lifecycle {
    @Test
    void testLifecycle() {
      ASpringTxManagedProjection p = new ASpringTxManagedProjection(transactionManager);

      SpringTransactionalLens uut = new SpringTransactionalLens(p, springTxManager, definition);

      uut.beforeFactProcessing(f);

      verify(springTxManager).startOrJoin();

      uut.doClear();

      verify(springTxManager).rollback();

      when(springTxManager.isRunning()).thenReturn(true);

      uut.doFlush();

      verify(springTxManager).commit();
    }
  }

  @Nested
  class WhenGettingSize {

    @Test
    void failsOnNoAnnotation() {
      assertThatThrownBy(() -> SpringTransactionalLens.getSize(new ProjectionWithoutAnnotation()))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class WhenCreatingOpts {
    @Test
    void failsOnNoAnnotation() {
      assertThatThrownBy(
              () -> SpringTransactionalLens.creatDefinition(new ProjectionWithoutAnnotation()))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class WhenCalculatingFlushTimeout {
    @Test
    void is8of10() {
      long result = SpringTransactionalLens.calculateFlushTimeout(1000);

      assertThat(result).isEqualTo(Duration.ofMillis(800).toMillis());
    }

    @Test
    void disableIfToSmall() {
      long result = SpringTransactionalLens.calculateFlushTimeout(10);

      assertThat(result).isEqualTo(0L);
    }

    @Test
    void edgeCase() {
      long result = SpringTransactionalLens.calculateFlushTimeout(100);

      assertThat(result).isEqualTo(Duration.ofMillis(80).toMillis());
    }

    @Test
    void conversionToMs() {
      when(definition.getTimeout()).thenReturn(1);

      SpringTxManagedProjection p = new ASpringTxManagedProjection(transactionManager);
      SpringTransactionalLens uut = new SpringTransactionalLens(p, springTxManager, definition);

      assertThat(uut.flushTimeout()).isEqualTo(800L);
    }
  }

  @Nested
  class WhenParameteringTransformerFor {

    @Test
    void returnsCurrentTx() {
      SpringTxManagedProjection p = new ASpringTxManagedProjection(transactionManager);

      SpringTransactionalLens underTest =
          new SpringTransactionalLens(p, springTxManager, definition);

      Function<Fact, ?> t = underTest.parameterTransformerFor(TransactionTemplate.class);
      assertThat(t).isNotNull();
      assertThat(t.apply(f))
          .isInstanceOf(TransactionTemplate.class)
          .extracting((x) -> ((TransactionTemplate) x).getTransactionManager())
          .isEqualTo(transactionManager);
    }

    @Test
    void returnsNullForOtherType() {
      SpringTxManagedProjection p = new ASpringTxManagedProjection(transactionManager);

      SpringTransactionalLens underTest =
          new SpringTransactionalLens(p, springTxManager, definition);

      Function<Fact, ?> t = underTest.parameterTransformerFor(Fact.class);
      assertThat(t).isNull();
    }
  }

  static class ProjectionWithoutAnnotation implements SpringTxProjection {
    @Override
    public PlatformTransactionManager platformTransactionManager() {
      return mock(PlatformTransactionManager.class);
    }
  }
}
