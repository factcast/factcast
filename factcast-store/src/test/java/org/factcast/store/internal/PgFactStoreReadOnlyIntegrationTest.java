/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(
    classes = {PgTestConfiguration.class, LiquibaseConfigurationForReadOnlyMode.class})
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@IntegrationTest
@TestPropertySource(properties = {"factcast.store.readOnlyModeEnabled=true"})
class PgFactStoreReadOnlyIntegrationTest {

  public static final Fact EMPTY_FACT =
      Fact.of("{\"id\":\"550e8400-e29b-11d4-a716-446655440000\", \"ns\":\"foo\"}", "{}");

  @Autowired FactStore store;

  @Test
  void doesThrowOnPublish() {
    assertThatThrownBy(() -> store.publish(List.of(EMPTY_FACT)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void doesThrowOnPublishIfUnchanged() {
    assertThatThrownBy(() -> store.publishIfUnchanged(List.of(EMPTY_FACT), Optional.empty()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void throwsOnCurrentState() {
    assertThatThrownBy(() -> store.currentStateFor(Lists.newArrayList()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void throwsOnCurrentInvalidState() {
    assertThatThrownBy(() -> store.invalidate(new StateToken()))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
