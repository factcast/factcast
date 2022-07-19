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
package org.factcast.itests.factus;

import static org.assertj.core.api.Assertions.assertThat;

import config.RedissonProjectionConfiguration;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.itests.factus.proj.UserV1;
import org.factcast.itests.factus.proj.UserV2;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@ContextConfiguration(classes = {Application.class, RedissonProjectionConfiguration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Slf4j
class FactusClientTestWithSchemaRegistry extends AbstractFactCastIntegrationTest {

  @Autowired Factus ec;

  @Test
  void testVersions_upcast() {
    // INIT
    UUID aggId = UUID.randomUUID();

    // RUN
    ec.publish(new org.factcast.itests.factus.event.versioned.v2.UserCreated(aggId, "foo", ""));

    // ASSERT
    // this should work anyways:
    UserV1 userV1 = ec.fetch(UserV1.class, aggId);
    assertThat(userV1.userName()).isEqualTo("foo");

    UserV2 userV2 = ec.fetch(UserV2.class, aggId);
    assertThat(userV2.userName()).isEqualTo("foo");
    assertThat(userV2.salutation()).isEqualTo("NA");
  }

  @Test
  void testVersions_downcast() {
    // INIT
    UUID aggId = UUID.randomUUID();

    // RUN
    ec.publish(new org.factcast.itests.factus.event.versioned.v2.UserCreated(aggId, "foo", "Mr"));

    // ASSERT
    // this should work anyways:
    UserV2 userV2 = ec.fetch(UserV2.class, aggId);
    assertThat(userV2.userName()).isEqualTo("foo");
    assertThat(userV2.salutation()).isEqualTo("Mr");

    UserV1 userV1 = ec.fetch(UserV1.class, aggId);
    assertThat(userV1.userName()).isEqualTo("foo");
  }
}
