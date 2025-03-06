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
package org.factcast.server.ui.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Lists;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.factcast.server.ui.views.filter.FactCriteria;
import org.junit.jupiter.api.Test;

class ReportFilterBeanTest {

  final ReportFilterBean underTest = new ReportFilterBean(123);

  @Test
  void resetWorks() {
    // ARRANGE
    underTest.setCriteria(
        Lists.newArrayList(
            new FactCriteria("namespace", Set.of("PizzaOrdered"), UUID.randomUUID(), List.of())));
    underTest.setFrom(BigDecimal.TEN);
    LocalDate originalSince = underTest.getSince();
    // ACT
    underTest.reset();
    // ASSERT
    assertThat(underTest.getCriteria()).hasSize(1);
    final var factCriteriaAfterReset = underTest.getCriteria().get(0);
    assertThat(factCriteriaAfterReset.getNs()).isNull();
    assertThat(factCriteriaAfterReset.getType()).isNull();
    assertThat(factCriteriaAfterReset.getAggId()).isNull();
    assertThat(underTest.getSince()).isAfterOrEqualTo(originalSince);
    assertThat(underTest.getFrom()).isEqualTo(BigDecimal.valueOf(123));
  }
}
