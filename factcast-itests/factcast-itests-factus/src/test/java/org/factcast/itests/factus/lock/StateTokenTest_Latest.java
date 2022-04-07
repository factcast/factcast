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
package org.factcast.itests.factus.lock;

import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.lock.Attempt;
import org.factcast.core.spec.FactSpec;
import org.factcast.itests.factus.Application;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.FactcastTestConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@Slf4j
@FactcastTestConfig(factcastVersion = "latest")
@SpringBootTest
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@ContextConfiguration(classes = {Application.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class StateTokenTest_Latest extends AbstractFactCastIntegrationTest {
  @Autowired FactCast fc;

  @Test
  public void issue1884() throws Exception {
    var fspec = FactSpec.ns("foo").type("bar");

    var facts =
        fc.lock(fspec)
            .optimistic()
            .attempt(
                () -> Attempt.publish(Fact.builder().ns("foo").type("bar").buildWithoutPayload()))
            .publishedFacts();
  }
}
