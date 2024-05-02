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
package org.factcast.factus.projection.tx;

import static org.junit.jupiter.api.Assertions.*;

import javax.annotation.Nullable;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.FactStreamPosition;
import org.junit.jupiter.api.Test;

class TransactionAwareTest {

  @Test
  void hasDefaultSize() {
    TransactionAware ta =
        new TransactionAware() {
          @Override
          public void begin() throws TransactionException {}

          @Override
          public void commit() throws TransactionException {}

          @Override
          public void rollback() throws TransactionException {}

          @Nullable
          @Override
          public FactStreamPosition factStreamPosition() {
            return null;
          }

          @Override
          public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {}
        };

    Assertions.assertThat(ta.maxBatchSizePerTransaction()).isEqualTo(1000);
  }
}
