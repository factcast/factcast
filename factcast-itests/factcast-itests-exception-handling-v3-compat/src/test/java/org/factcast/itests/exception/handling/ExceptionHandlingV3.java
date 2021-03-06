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
package org.factcast.itests.exception.handling;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import io.grpc.StatusRuntimeException;
import java.util.Collections;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.FactValidationException;
import org.factcast.core.lock.Attempt;
import org.factcast.core.spec.FactSpec;
import org.factcast.factus.Factus;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/** client version 0.3.9 against latest factcast */
@SpringBootTest
@EnableAutoConfiguration
@ContextConfiguration(classes = {Application.class})
@Slf4j
public class ExceptionHandlingV3 extends AbstractFactCastIntegrationTest {

  @Autowired Factus ec;

  @Autowired FactCast fc;

  @Test
  public void testPublish_validationFailed() {
    // INIT
    UUID aggId = UUID.randomUUID();

    // RUN
    assertThatThrownBy(
            () ->
                ec.publish(
                    createTestFact(
                        aggId,
                        2,
                        "{\"firstName\":\"Peter\",\"lastName\":\"Zwegert\",\"salutation\":\"FOO\"}")))
        .isInstanceOf(FactValidationException.class);
  }

  @Test
  public void testPublish_validationFailed_withLockOn() {
    // INIT
    UUID aggId = UUID.randomUUID();

    // RUN
    assertThatThrownBy(
            () ->
                ec.withLockOn(Collections.singletonList(FactSpec.ns("users")))
                    .attempt(
                        (tx) ->
                            tx.publish(
                                createTestFact(
                                    aggId,
                                    2,
                                    "{\"firstName\":\"Peter\",\"lastName\":\"Zwegert\",\"salutation\":\"FOO\"}"))))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessage("UNKNOWN");
  }

  @Test
  public void testProjection_transformationErrors() {
    // INIT
    UUID aggId = UUID.randomUUID();

    ec.publish(createTestFact(aggId, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Zwegert\"}"));

    val proj = new LocalManagedUserNames();
    assertDoesNotThrow(() -> ec.update(proj));
    assertThat(proj.exception()).isNull();
  }

  @Test
  public void failingTransformation() {

    UUID id = UUID.randomUUID();
    Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Zwegert\"}");
    fc.publish(f);

    assertThatThrownBy(() -> fc.fetchByIdAndVersion(id, 2))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessage("UNKNOWN");
  }

  @Test
  public void validationFailsOnSchemaViolation() {
    Fact brokenFact = createTestFact(UUID.randomUUID(), 1, "{}");
    assertThatThrownBy(() -> fc.publish(brokenFact)).isInstanceOf(FactValidationException.class);
  }

  @Test
  public void validationFailsOnSchemaViolation_withinLock() {
    Fact brokenFact = createTestFact(UUID.randomUUID(), 1, "{}");

    assertThatThrownBy(
            () -> fc.lock(FactSpec.ns("users")).attempt(() -> Attempt.publish(brokenFact)))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessage("UNKNOWN");
  }

  private Fact createTestFact(UUID id, int version, String body) {
    return Fact.builder().ns("users").type("UserCreated").id(id).version(version).build(body);
  }
}
