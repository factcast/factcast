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
package org.factcast.test;

import java.lang.reflect.Field;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.ModifierSupport;
import org.junit.platform.commons.util.ReflectionUtils;
import org.testcontainers.containers.PostgreSQLContainer;

@Slf4j
public class FactCastExtension implements Extension, BeforeEachCallback {

  @Override
  public void beforeEach(ExtensionContext extensionContext) throws Exception {
    Class<?> testClass =
        extensionContext
            .getTestClass()
            .orElseThrow(() -> new IllegalArgumentException("TestClass cannot be resolved"));

    while (testClass.getEnclosingClass() != null) {
      testClass = testClass.getEnclosingClass();
    }

    val pg = findPG(testClass);

    if (pg.isPresent()) {
      log.debug("Wiping FactCast data from postgresql");
      PostgresEraser.wipeAllFactCastDataDataFromPostgres(pg.get());
    } else {
      log.warn(
          "No static field of type {} found, so wiping data from Postgres was not possible.",
          PostgreSQLContainer.class.getName());
    }
  }

  private Optional<? extends PostgreSQLContainer<?>> findPG(Class<?> testClass) {
    return org.junit.platform.commons.util.ReflectionUtils.findFields(
            testClass,
            FactCastExtension::isPostgresContainer,
            ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
        .stream()
        .map(
            f -> {
              try {
                f.setAccessible(true);
                return (PostgreSQLContainer<?>) f.get(null);
              } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(
                    "Cannot get value from (supposedly static) field " + f);
              }
            })
        .findAny();
  }

  private static boolean isPostgresContainer(Field f) {
    return ModifierSupport.isStatic(f) && PostgreSQLContainer.class.isAssignableFrom(f.getType());
  }
}
