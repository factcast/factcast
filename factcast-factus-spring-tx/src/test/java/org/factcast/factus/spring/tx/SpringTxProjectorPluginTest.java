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
