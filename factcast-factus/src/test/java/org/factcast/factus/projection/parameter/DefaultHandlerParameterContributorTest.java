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
package org.factcast.factus.projection.parameter;

import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.TestFact;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Projection;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.HashSet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultHandlerParameterContributorTest {
  @Test
  void providesFSP() {
    DefaultHandlerParameterContributor undertest =
        new DefaultHandlerParameterContributor(mock(EventSerializer.class));
    HandlerParameterProvider provider =
        undertest.providerFor(FactStreamPosition.class, new HashSet<>());

    Fact fact = new TestFact();
    FactStreamPosition fsp = FactStreamPosition.from(fact);
    TestProjection p = mock(TestProjection.class);
    when(p.factStreamPosition()).thenReturn(fsp);

    Assertions.assertThat(provider.apply(fact, p)).isEqualTo(fsp);
  }

  static class TestProjection implements Projection, FactStreamPositionAware {
    @Nullable
    @Override
    public FactStreamPosition factStreamPosition() {
      return null;
    }

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {}
  }
}
