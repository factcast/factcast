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
package org.factcast.server.ui.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.factcast.server.ui.views.filter.FactCriteria;
import org.junit.jupiter.api.Test;

class ReportTest {

  @Test
  @SneakyThrows
  void serialization() {

    final var om = new ObjectMapper();
    om.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    ObjectNode event = om.getNodeFactory().objectNode();
    event.put("foo", "bar");

    final var criteria = new FactCriteria();
    criteria.setNs("ns");
    criteria.setType(Set.of("type"));
    final var qb = new ReportFilterBean(0, BigDecimal.TEN, List.of(criteria));

    final var report = new Report("event.json", List.of(event), qb);

    final var serialized = om.writeValueAsString(report);
    final var deserialized = om.readValue(serialized, Report.class);

    assertThat(deserialized.name()).isEqualTo(report.name());
    assertThat(deserialized.events()).isEqualTo(report.events());
    assertThat(deserialized.query().getFrom()).isEqualTo(report.query().getFrom());
    assertThat(deserialized.query().getDefaultFrom()).isEqualTo(report.query().getDefaultFrom());
    assertThat(deserialized.query().getCriteria())
        .extracting(FactCriteria::getNs, FactCriteria::getType)
        .hasSize(1)
        .contains(Tuple.tuple("ns", Set.of("type")));
  }
}
