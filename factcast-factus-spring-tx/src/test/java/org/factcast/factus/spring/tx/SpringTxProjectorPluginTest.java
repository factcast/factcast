package org.factcast.factus.spring.tx;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.factcast.factus.projection.Projection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class SpringTxProjectorPluginTest {
  @Mock private PlatformTransactionManager transactionManager;
  @InjectMocks private SpringTxProjectorPlugin uut;

  @Nested
  class WhenLensingFor {
    @Mock private Projection projection;

    @Test
    void testLensFor_happyCase() {
      assertThat(uut.lensFor(new ASpringTxSubscribedProjection(transactionManager)))
          .hasSize(1)
          .first()
          .isInstanceOf(SpringTransactionalLens.class);
    }

    @Test
    void testLensFor_noAnnotation() {
      assertThatThrownBy(() -> uut.lensFor(new ProjectionWithoutAnnotation()))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testLensFor_noSpringTxProjection() {
      assertThat(uut.lensFor(projection)).isEmpty();
    }
  }

  static class ProjectionWithoutAnnotation implements SpringTxProjection {
    @Override
    public PlatformTransactionManager platformTransactionManager() {
      return mock(PlatformTransactionManager.class);
    }
  }
}
