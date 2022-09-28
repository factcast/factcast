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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.subscription.transformation.MissingTransformationInformationException;
import org.factcast.store.registry.transformation.cache.PgTransformationCache;
import org.factcast.store.registry.transformation.cache.PgTransformationStoreChangeListener;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@IntegrationTest
public class TransformationCacheTest {

  @Autowired FactCast fc;

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired TransformationCache transformationCache;

  @SpyBean PgTransformationStoreChangeListener listener;

  @Nested
  @DirtiesContext
  class whenDeletingFromTransformationStore {
    @Test
    public void transformationCacheIsInvalidated() throws Exception {
      CountDownLatch wasOned = new CountDownLatch(1);
      Mockito.doAnswer(
              spy -> {
                spy.callRealMethod();
                wasOned.countDown();
                return null;
              })
          .when(listener)
          .on(any());
      UUID id = UUID.randomUUID();
      Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
      fc.publish(f);
      fc.fetchByIdAndVersion(id, 2).get();
      fc.fetchByIdAndVersion(id, 3).get();
      // force cache flush
      ((PgTransformationCache) transformationCache).flush();

      jdbcTemplate.update(
          String.format(
              "DELETE FROM transformationstore WHERE type='%s' AND from_version=%d", f.type(), 2));
      wasOned.await();

      assertDoesNotThrow(() -> fc.fetchByIdAndVersion(id, 1));
      assertDoesNotThrow(() -> fc.fetchByIdAndVersion(id, 2));
      assertThrows(
          MissingTransformationInformationException.class, () -> fc.fetchByIdAndVersion(id, 3));
    }
  }

  @Nested
  @DirtiesContext
  class whenUpdatingTransformationStore {
    @Test
    public void transformationCacheIsInvalidated() throws Exception {
      CountDownLatch wasOned = new CountDownLatch(1);
      Mockito.doAnswer(
              spy -> {
                spy.callRealMethod();
                wasOned.countDown();
                return null;
              })
          .when(listener)
          .on(any());
      UUID id = UUID.randomUUID();
      Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
      fc.publish(f);
      fc.fetchByIdAndVersion(id, 2).get();
      fc.fetchByIdAndVersion(id, 3).get();
      // force cache flush
      ((PgTransformationCache) transformationCache).flush();

      String randomUUID = UUID.randomUUID().toString();
      jdbcTemplate.update(
          String.format(
              "UPDATE transformationstore SET transformation='function transform(event){event.displayName=\"%s\"}' WHERE type='%s' AND from_version=%d AND to_version=%d",
              randomUUID, f.type(), 2, 3));
      wasOned.await();

      assertDoesNotThrow(() -> fc.fetchByIdAndVersion(id, 1));
      assertDoesNotThrow(() -> fc.fetchByIdAndVersion(id, 2));
      assertThat(fc.fetchByIdAndVersion(id, 3).get().jsonPayload()).contains(randomUUID);
    }
  }

  private Fact createTestFact(UUID id, int version, String body) {
    return Fact.builder().ns("users").type("UserCreated").id(id).version(version).build(body);
  }
}
