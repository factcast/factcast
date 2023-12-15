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
package org.factcast.server.ui.full;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FullQueryBeanTest {

  final FullQueryBean underTest = new FullQueryBean(12);

  @Nested
  class WhenCreatingFactSpecs {
    @Test
    void specsFull() {
      UUID aggId = UUID.randomUUID();
      FactCriteria criteria = underTest.getCriteria().get(0);
      criteria.setNs("n");
      criteria.setType(Set.of("foo", "bar"));
      criteria.setAggId(aggId);

      MetaTuple m1 = new MetaTuple();
      m1.setKey("m1");
      m1.setValue("1");

      MetaTuple m2 = new MetaTuple();
      m2.setKey("m2");
      m2.setValue("2");

      criteria.setMeta(List.of(m1, m2));
      criteria.setAggId(aggId);

      Assertions.assertThat(underTest.createFactSpecs())
          .hasSize(2)
          .contains(
              FactSpec.ns("n").type("foo").version(0).meta("m1", "1").meta("m2", "2").aggId(aggId))
          .contains(
              FactSpec.ns("n").type("bar").version(0).meta("m1", "1").meta("m2", "2").aggId(aggId));
    }

    @Test
    void specsWithTypeNoMeta() {
      FactCriteria criteria = underTest.getCriteria().get(0);
      criteria.setNs("n");
      criteria.setType(Set.of("foo", "bar"));

      Assertions.assertThat(underTest.createFactSpecs())
          .hasSize(2)
          .contains(FactSpec.ns("n").type("foo").version(0))
          .contains(FactSpec.ns("n").type("bar").version(0));
    }

    @Test
    void specsNoType() {
      UUID aggId = UUID.randomUUID();
      FactCriteria criteria = underTest.getCriteria().get(0);
      criteria.setNs("n");
      criteria.setAggId(aggId);

      MetaTuple m1 = new MetaTuple();
      m1.setKey("m1");
      m1.setValue("1");

      MetaTuple m2 = new MetaTuple();
      m2.setKey("m2");
      m2.setValue("2");

      criteria.setMeta(List.of(m1, m2));
      criteria.setAggId(aggId);

      Assertions.assertThat(underTest.createFactSpecs())
          .hasSize(1)
          .contains(FactSpec.ns("n").version(0).meta("m1", "1").meta("m2", "2").aggId(aggId));
    }

    void specsNoTypeNoMeta() {
      FactCriteria criteria = underTest.getCriteria().get(0);
      criteria.setNs("n");

      Assertions.assertThat(underTest.createFactSpecs())
          .hasSize(1)
          .contains(FactSpec.ns("n").version(0));
    }

    @Test
    void specsNsOnly() {
      FactCriteria criteria = underTest.getCriteria().get(0);
      criteria.setNs("n");
      Assertions.assertThat(underTest.createFactSpecs()).hasSize(1).contains(FactSpec.ns("n"));
    }

    @Test
    void specsThrowsIfNsMissing() {
      Assertions.assertThatThrownBy(() -> underTest.createFactSpecs())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class WhenReseting {
    @Test
    void returnsOffset() {
      // even though it is annotated as non null, we need it to be null after reset
      underTest.reset();
      FactCriteria criteria = underTest.getCriteria().get(0);
      Assertions.assertThat(criteria.getNs()).isNull();
    }
  }

  @Nested
  class WhenGettingOffsetOrDefault {
    @Test
    void returnsOffset() {
      underTest.setOffset(4);
      Assertions.assertThat(underTest.getOffsetOrDefault()).isEqualTo(4);
    }

    @Test
    void returnsDefault() {
      Assertions.assertThat(underTest.getOffsetOrDefault()).isZero();
    }
  }

  @Nested
  class WhenGettingLimitOrDefault {
    @Test
    void returnsOffset() {
      underTest.setLimit(4);
      Assertions.assertThat(underTest.getLimitOrDefault()).isEqualTo(4);
    }

    @Test
    void returnsDefault() {
      Assertions.assertThat(underTest.getLimitOrDefault()).isEqualTo(FullQueryBean.DEFAULT_LIMIT);
    }
  }
}
