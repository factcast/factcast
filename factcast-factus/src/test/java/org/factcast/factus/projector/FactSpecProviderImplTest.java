/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.factus.projector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.List;
import org.factcast.core.spec.FactSpec;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projection.SnapshotProjection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactSpecProviderImplTest {

  @Mock ProjectorFactory projectorFactory;
  @Mock Projector projector;

  @InjectMocks FactSpecProviderImpl underTest;

  @Nested
  class ForProjection {
    @Test
    void delegatesToProjectorFactoryAndReturnsSpecs() {
      Projection projection = mock(Projection.class);
      FactSpec factSpec = mock(FactSpec.class);
      List<FactSpec> specs = List.of(factSpec);
      when(projectorFactory.create(projection)).thenReturn(projector);
      when(projector.createFactSpecs()).thenReturn(specs);

      Collection<FactSpec> result = underTest.forProjection(projection);

      assertThat(result).isSameAs(specs);
      verify(projectorFactory).create(projection);
      verify(projector).createFactSpecs();
    }

    @Test
    void rejectsNullProjection() {
      assertThatThrownBy(() -> underTest.forProjection(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("p is marked non-null but is null");
    }
  }

  @Nested
  class ForSnapshot {
    @Test
    void instantiatesSnapshotProjectionAndDelegatesToProjectorFactory() {
      FactSpec factSpec = mock(FactSpec.class);
      List<FactSpec> specs = List.of(factSpec);
      when(projectorFactory.create(any(TestSnapshotProjection.class))).thenReturn(projector);
      when(projector.createFactSpecs()).thenReturn(specs);

      Collection<FactSpec> result = underTest.forSnapshot(TestSnapshotProjection.class);

      assertThat(result).isSameAs(specs);
      verify(projectorFactory)
          .create(argThat(p -> p != null && p.getClass().equals(TestSnapshotProjection.class)));
      verify(projector).createFactSpecs();
    }

    @Test
    void rejectsNullSnapshotClass() {
      assertThatThrownBy(() -> underTest.forSnapshot(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("clazz is marked non-null but is null");
    }
  }

  static class TestSnapshotProjection implements SnapshotProjection {}
}
