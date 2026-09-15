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
package org.factcast.factus.spring.tx.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProjectionNamesTest {

  private static String repeat(int length) {
    return "x".repeat(length);
  }

  @Nested
  class WhenNameFitsTheColumn {

    @Test
    void keepsLockNameVerbatim() {
      assertThat(ProjectionNames.lockName("org.example.MyProjection_1"))
          .isEqualTo("org.example.MyProjection_1_lock");
    }

    @Test
    void keepsPositionNameVerbatim() {
      assertThat(ProjectionNames.positionName("org.example.MyProjection_1"))
          .isEqualTo("org.example.MyProjection_1");
    }

    @Test
    void keepsNameOfExactlyMaxLengthVerbatim() {
      String name = repeat(ProjectionNames.MAX_NAME_LENGTH);

      assertThat(ProjectionNames.shorten(name)).isEqualTo(name);
    }
  }

  @Nested
  class WhenNameExceedsTheColumn {

    @Test
    void appendsHashSuffix() {
      String name = repeat(300);

      String shortened = ProjectionNames.shorten(name);

      assertThat(shortened).hasSize(209).startsWith(repeat(200) + "_");
      assertThat(shortened.substring(201)).hasSize(8).matches("[0-9a-f]{8}");
    }

    @Test
    void shortensJustOverMaxLength() {
      assertThat(ProjectionNames.shorten(repeat(ProjectionNames.MAX_NAME_LENGTH + 1)))
          .hasSize(209)
          .isNotEqualTo(repeat(ProjectionNames.MAX_NAME_LENGTH + 1));
    }

    @Test
    void doesNotCollideForNamesSharingTheFirst255Chars() {
      String shared = repeat(255);

      String first = ProjectionNames.lockName(shared + "Aggregate");
      String second = ProjectionNames.lockName(shared + "Snapshot");

      assertThat(first).isNotEqualTo(second);
      assertThat(first).hasSize(209);
      assertThat(second).hasSize(209);
    }

    @Test
    void isStableAcrossInvocations() {
      String name = repeat(400);

      assertThat(ProjectionNames.shorten(name)).isEqualTo(ProjectionNames.shorten(name));
    }

    @Test
    void producesDifferentNamesForLockAndPosition() {
      String name = repeat(300);

      assertThat(ProjectionNames.lockName(name)).isNotEqualTo(ProjectionNames.positionName(name));
    }
  }
}
