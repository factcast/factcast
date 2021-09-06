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
package org.factcast.itests.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.FactValidationException;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.MissingTransformationInformationException;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.util.FactCastJson;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TransformationTest {

  @Autowired FactCast fc;

  @Test
  public void publishAndFetchBack() {
    UUID id = UUID.randomUUID();
    Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
    fc.publish(f);

    final var found = fc.fetchById(id).orElse(null);
    assertNotNull(found);
    assertEquals(f.ns(), found.ns());
    assertEquals(f.type(), found.type());
    assertEquals(f.id(), found.id());
  }

  @Test
  public void publishV1AndFetchBackAsV2() throws Exception {

    UUID id = UUID.randomUUID();
    Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
    fc.publish(f);

    final var found = fc.fetchByIdAndVersion(id, 2).orElse(null);

    assertNotNull(found);
    assertEquals(f.ns(), found.ns());
    assertEquals(f.type(), found.type());
    assertEquals(f.id(), found.id());
    assertEquals(2, found.version());
  }

  private Fact createTestFact(UUID id, int version, String body) {
    return Fact.builder().ns("users").type("UserCreated").id(id).version(version).build(body);
  }

  @Test
  public void publishV2AndFetchBackAsV1() throws Exception {

    UUID id = UUID.randomUUID();
    Fact f =
        createTestFact(
            id, 2, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\"}");
    fc.publish(f);

    final var found = fc.fetchByIdAndVersion(id, 1).orElse(null);
    assertNotNull(found);
    assertEquals(f.ns(), found.ns());
    assertEquals(f.type(), found.type());
    assertEquals(f.id(), found.id());
    assertEquals(1, found.version());
  }

  @Test
  public void downcastUsesNonSyntheticTransformation() throws Exception {

    UUID id = UUID.randomUUID();
    Fact f =
        createTestFact(
            id,
            3,
            "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\",\"displayName\":\"PETER PETERSON\"}");
    fc.publish(f);

    final var found = fc.fetchByIdAndVersion(id, 1).orElse(null);
    assertNotNull(found);
    assertEquals(f.ns(), found.ns());
    assertEquals(f.type(), found.type());
    assertEquals(f.id(), found.id());
    assertEquals(1, found.version());
    assertTrue(getBoolean(found, "wasDowncastedFromVersion3to2"));
  }

  @Test
  public void returnsOriginalIfNoVersionSet() {

    UUID id = UUID.randomUUID();
    Fact f =
        createTestFact(
            id,
            3,
            "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\",\"displayName\":\"PETER PETERSON\"}");
    fc.publish(f);
    final var found = fc.fetchById(id).orElse(null);
    assertNotNull(found);
    assertEquals(3, found.version());
  }

  @Test
  public void returnsOriginalIfVersionSetTo0() throws Exception {

    UUID id = UUID.randomUUID();
    Fact f =
        createTestFact(
            id,
            3,
            "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\",\"displayName\":\"PETER PETERSON\"}");
    fc.publish(f);

    final var found = fc.fetchByIdAndVersion(id, 0).orElse(null);
    assertNotNull(found);
    assertEquals(3, found.version());
  }

  @Test
  public void publishV1AndFetchBackAsV3() throws Exception {

    UUID id = UUID.randomUUID();
    Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
    fc.publish(f);

    final var found = fc.fetchByIdAndVersion(id, 3).orElse(null);
    assertNotNull(found);
    assertEquals(f.ns(), found.ns());
    assertEquals(f.type(), found.type());
    assertEquals(f.id(), found.id());
    assertEquals(3, found.version());
    assertEquals("Peter Peterson", getString(found, "displayName"));
  }

  @Test
  public void publishV1AndFetchBackAsUnknownVersionMustFail() {

    UUID id = UUID.randomUUID();
    Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
    fc.publish(f);

    try {
      fc.fetchByIdAndVersion(id, 999).orElse(null);
      fail("should have thrown");
    } catch (MissingTransformationInformationException expected) {
    } catch (Exception anyOther) {
      fail("unexpected Exception", anyOther);
    }
  }

  @Test
  public void publishAndSubscribe_fromCacheOrTransformed() {
    // INIT
    UUID id1 = UUID.randomUUID();
    fc.publish(
        createTestFact(id1, 1, "{\"firstName\":\"Cached\",\"lastName\":\"Needs transformation\"}"));
    // put version in cache, but not the one we want
    fc.fetchByIdAndVersion(id1, 2);

    UUID id2 = UUID.randomUUID();
    fc.publish(
        createTestFact(
            id2, 1, "{\"firstName\":\"Cached\",\"lastName\":\"Does not need transformation\"}"));
    // put the version in cache that we want
    fc.fetchByIdAndVersion(id2, 3);

    UUID id3 = UUID.randomUUID();
    // not the version we want, but do not put into cache
    fc.publish(
        createTestFact(
            id3, 1, "{\"firstName\":\"Not Cached\",\"lastName\":\"Needs transformation\"}"));

    UUID id4 = UUID.randomUUID();
    // publish the version we want
    fc.publish(
        createTestFact(
            id4,
            3,
            "{\"firstName\":\"Not Cached\",\"lastName\":\"Does not need transformation\", "
                + "\"salutation\":\"Mr\",\"displayName\":\"Not Cached Does not need transformation\"}"));

    UUID id5 = UUID.randomUUID();
    // publish the version we want
    fc.publish(
        createTestFact(
            id5,
            3,
            "{\"firstName\":\"Not Cached 2\",\"lastName\":\"Does not need transformation 2\", "
                + "\"salutation\":\"Mr\",\"displayName\":\"Not Cached 2 Does not need transformation 2\"}"));

    // RUN
    SubscriptionRequest req =
        SubscriptionRequest.catchup(
                FactSpec.ns("users")
                    .type("UserCreated")
                    .version(3)
                    .jsFilterScript(
                        "function (h,e){ return e.firstName.indexOf('Cached') != -1; }"))
            .fromScratch();

    List<Fact> facts = new ArrayList<>();
    fc.subscribe(req, facts::add).awaitCatchup();

    // ASSERT
    assertThat(facts)
        .hasSize(5)
        .extracting(Fact::id, Fact::version)
        .containsExactlyInAnyOrder(
            tuple(id1, 3), tuple(id2, 3), tuple(id3, 3), tuple(id4, 3), tuple(id5, 3));
  }

  @Test
  public void validationFailsOnSchemaViolation() {
    Fact brokenFact = createTestFact(UUID.randomUUID(), 1, "{}");
    assertThrows(FactValidationException.class, () -> fc.publish(brokenFact));
  }

  private String getString(Fact f, String name) throws JsonProcessingException {
    return FactCastJson.readTree(f.jsonPayload()).path(name).asText();
  }

  private boolean getBoolean(Fact f, String name) throws JsonProcessingException {
    return FactCastJson.readTree(f.jsonPayload()).path(name).asBoolean();
  }
}
