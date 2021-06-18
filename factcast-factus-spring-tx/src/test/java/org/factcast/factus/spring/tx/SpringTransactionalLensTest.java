package org.factcast.factus.spring.tx;

import jdk.nashorn.internal.runtime.URIUtils;
import lombok.val;
import org.factcast.core.Fact;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.config.TxNamespaceHandler;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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
      val p = new ASpringTxManagedProjection(transactionManager);

      val uut = new SpringTransactionalLens(p, springTxManager, definition);

      uut.beforeFactProcessing(f);

      verify(springTxManager).startOrJoin();

      uut.doClear();

      verify(springTxManager).rollback();

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
      DefaultTransactionDefinition opts = new DefaultTransactionDefinition();
      opts.setTimeout(1000);

      long result = SpringTransactionalLens.calculateFlushTimeout(opts);

      assertThat(result).isEqualTo(Duration.ofMillis(800).toMillis());
    }

    @Test
    void disableIfToSmall() {
      DefaultTransactionDefinition opts = new DefaultTransactionDefinition();
      opts.setTimeout(10);

      long result = SpringTransactionalLens.calculateFlushTimeout(opts);

      assertThat(result).isEqualTo(0L);
    }

    @Test
    void edgeCase() {
      DefaultTransactionDefinition opts = new DefaultTransactionDefinition();
      opts.setTimeout(100);

      long result = SpringTransactionalLens.calculateFlushTimeout(opts);

      assertThat(result).isEqualTo(Duration.ofMillis(80).toMillis());
    }
  }

  @Nested
  class WhenParameteringTransformerFor {

    @Test
    void returnsCurrentTx() {
      SpringTxManagedProjection p = new ASpringTxManagedProjection(transactionManager);

      val underTest = new SpringTransactionalLens(p, springTxManager, definition);

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

      val underTest = new SpringTransactionalLens(p, springTxManager, definition);

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
