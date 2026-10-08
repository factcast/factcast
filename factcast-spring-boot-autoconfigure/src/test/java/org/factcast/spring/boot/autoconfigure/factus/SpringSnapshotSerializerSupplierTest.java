/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.spring.boot.autoconfigure.factus;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.snapshot.SnapshotSerializerSupplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class SpringSnapshotSerializerSupplierTest {

  @Mock private @NonNull ApplicationContext ctx;
  @Mock private @NonNull SnapshotSerializerSupplier parent;
  @InjectMocks private SpringSnapshotSerializerSupplier underTest;

  @Nested
  class WhenCreating {
    private final Class<TestSerializer> type = TestSerializer.class;
    @Mock TestSerializer mock;

    @Test
    void prefersBean() {
      when(ctx.getBean(type)).thenReturn(mock);

      Assertions.assertThat(underTest.get(type)).isSameAs(mock);
      verifyNoInteractions(parent);
    }

    @Test
    void fallsBackOnRandomException() {
      when(ctx.getBean(type)).thenThrow(new RuntimeException());
      when(parent.get(type)).thenReturn(mock);

      Assertions.assertThat(underTest.get(type)).isSameAs(mock);
    }

    @Test
    void fallsBackOnNoSuchBeanException() {
      when(ctx.getBean(type)).thenThrow(new NoSuchBeanDefinitionException(""));
      when(parent.get(type)).thenReturn(mock);

      Assertions.assertThat(underTest.get(type)).isSameAs(mock);
    }
  }

  abstract static class TestSerializer implements SnapshotSerializer {}
}
