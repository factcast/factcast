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
package org.factcast.itests.store;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.FactValidationException;
import org.factcast.store.registry.PgSchemaStoreChangeListener;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.test.IntegrationTest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.StreamUtils;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@IntegrationTest
public class SchemaCacheTest {

  @Autowired FactCast fc;

  @Autowired JdbcTemplate jdbcTemplate;

  @SpyBean PgSchemaStoreChangeListener listener;

  @Nested
  @DirtiesContext
  class whenDeletingFromSchemaStore {
    @Test
    public void schemaCacheIsInvalidated() throws Exception {
      CountDownLatch wasOned = new CountDownLatch(1);
      Mockito.doAnswer(
              spy -> {
                spy.callRealMethod();
                wasOned.countDown();
                return null;
              })
          .when(listener)
          .on(any());
      UUID idv1 = UUID.randomUUID();
      Fact v1 = createTestFact(idv1, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
      fc.publish(v1);
      UUID idv3 = UUID.randomUUID();
      Fact v3 =
          createTestFact(
              idv3,
              3,
              "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\",\"displayName\":\"v3\"}");
      fc.publish(v3);
      // fetching to populate the registry cache
      assertTrue(fc.fetchByIdAndVersion(idv1, 1).isPresent());
      assertTrue(fc.fetchByIdAndVersion(idv3, 3).isPresent());

      jdbcTemplate.update(
          String.format("DELETE FROM schemastore WHERE type='%s' AND version=%d", v3.type(), 3));
      wasOned.await();

      assertDoesNotThrow(() -> fc.fetchByIdAndVersion(idv1, 1));
      // assuming that deleting from the schemastore should not remove/change automatically existing
      // facts
      // consider handling this if the requirement changes
      assertDoesNotThrow(() -> fc.fetchByIdAndVersion(idv3, 3));
      Fact v3Again =
          createTestFact(
              UUID.randomUUID(),
              3,
              "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\",\"displayName\":\"v3Again\"}");
      assertThrows(FactValidationException.class, () -> fc.publish(v3Again));
    }
  }

  @Nested
  @DirtiesContext
  class whenUpdatingTransformationStore {
    @Test
    public void schemaCacheIsInvalidated() throws Exception {
      CountDownLatch wasOned = new CountDownLatch(1);
      Mockito.doAnswer(
              spy -> {
                spy.callRealMethod();
                wasOned.countDown();
                return null;
              })
          .when(listener)
          .on(any());
      UUID idv1 = UUID.randomUUID();
      Fact v1 = createTestFact(idv1, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
      fc.publish(v1);
      UUID idv3 = UUID.randomUUID();
      Fact v3 =
          createTestFact(
              idv3,
              3,
              "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\",\"displayName\":\"v3\"}");
      fc.publish(v3);
      // fetching to populate the registry cache
      assertTrue(fc.fetchByIdAndVersion(idv1, 1).isPresent());
      assertTrue(fc.fetchByIdAndVersion(idv3, 3).isPresent());

      String schemaV3 =
          StreamUtils.copyToString(
              new ClassPathResource("/example-registry/users/UserCreated/3/schema.json")
                  .getInputStream(),
              Charset.defaultCharset());
      JSONObject newSchemaV3 = new JSONObject(schemaV3);
      newSchemaV3
          .getJSONObject("properties")
          .put("newProperty", new JSONObject().put("type", "string"));
      newSchemaV3.put(
          "required",
          new JSONArray()
              .putAll(
                  List.of("firstName", "lastName", "salutation", "displayName", "newProperty")));

      jdbcTemplate.update(
          String.format(
              "UPDATE schemastore SET jsonschema='%s' WHERE type='%s' AND version=%d",
              newSchemaV3, v3.type(), 3));
      wasOned.await();

      assertDoesNotThrow(() -> fc.fetchByIdAndVersion(idv1, 1));
      // assuming that updating the schemastore should not remove/change automatically existing
      // facts
      // consider handling this if the requirement changes
      assertDoesNotThrow(() -> fc.fetchByIdAndVersion(idv3, 3));
      Fact v3Again =
          createTestFact(
              UUID.randomUUID(),
              3,
              "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\",\"displayName\":\"v3Again\"}");
      assertThrows(FactValidationException.class, () -> fc.publish(v3Again));
      Fact v3New =
          createTestFact(
              UUID.randomUUID(),
              3,
              "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\",\"salutation\":\"Mr\",\"displayName\":\"v3New\",\"newProperty\":\"test\"}");
      assertDoesNotThrow(() -> fc.publish(v3New));
    }
  }

  private Fact createTestFact(UUID id, int version, String body) {
    return Fact.builder().ns("users").type("UserCreated").id(id).version(version).build(body);
  }
}
