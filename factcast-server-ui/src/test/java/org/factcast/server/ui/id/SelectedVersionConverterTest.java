/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.server.ui.id;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.data.binder.Result;
import com.vaadin.flow.data.binder.ValueContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SelectedVersionConverterTest {

  private final SelectedVersionConverter uut = new SelectedVersionConverter();

  @Mock private ValueContext context;

  @Nested
  class WhenConverting {
    @Test
    void convertsAsPublished() {
      final var res = uut.convertToModel("as published", context);

      assertThat(res).isEqualTo(Result.ok(0));
    }

    @Test
    void convertsInteger() {
      final var res = uut.convertToModel("2", context);

      assertThat(res).isEqualTo(Result.ok(2));
    }

    @Test
    void convertsNullToDefault() {
      final var res = uut.convertToModel(null, context);

      assertThat(res).isEqualTo(Result.ok(0));
    }

    @Test
    void returnsErrorIfConversionFails() {
      final var res = uut.convertToModel("foo", context);

      assertThat(res.isError()).isTrue();
      assertThat(res.getMessage().orElseThrow())
          .isEqualTo("Failed to convert String foo to selected version");
    }
  }

  @Nested
  class WhenConvertingToPresentation {
    @Test
    void convertsZeroToAsPublished() {
      final var res = uut.convertToPresentation(0, context);

      assertThat(res).isEqualTo("as published");
    }

    @Test
    void convertsNullToAsPublished() {
      final var res = uut.convertToPresentation(null, context);

      assertThat(res).isEqualTo("as published");
    }

    @Test
    void convertsInteger() {
      final var res = uut.convertToPresentation(3, context);

      assertThat(res).isEqualTo("3");
    }
  }
}
