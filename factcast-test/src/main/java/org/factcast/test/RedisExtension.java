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
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.ModifierSupport;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode;
import org.testcontainers.containers.GenericContainer;

@Slf4j
public class RedisExtension implements Extension, BeforeEachCallback {

  @Override
  public void beforeEach(ExtensionContext extensionContext) throws Exception {

    Class<?> testClass =
        extensionContext
            .getTestClass()
            .orElseThrow(() -> new IllegalArgumentException("TestClass cannot be resolved"));

    while (testClass.getEnclosingClass() != null) {
      testClass = testClass.getEnclosingClass();
    }

    val redis = findRedis(testClass);

    if (redis.isPresent()) {
      log.debug("Wiping Redis");
      RedisEraser.wipeAllDataFromRedis(redis.get());
    } else {
      log.warn(
          "No static field of type {} found, so wiping data from Redis was not possible.",
          GenericContainer.class.getCanonicalName());
    }
  }

  private Optional<? extends GenericContainer<?>> findRedis(Class<?> testClass) {
    return ReflectionUtils.findFields(
            testClass, RedisExtension::isRedisContainer, HierarchyTraversalMode.TOP_DOWN)
        .stream()
        .filter(c -> GenericContainer.class.isAssignableFrom(c.getType()))
        .map(
            f -> {
              try {
                f.setAccessible(true);
                return (GenericContainer<?>) f.get(null);
              } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(
                    "Cannot get value from (supposedly static) field " + f);
              }
            })
        .findAny();
  }

  private static boolean isRedisContainer(Field f) {
    try {
      return ModifierSupport.isStatic(f)
          && GenericContainer.class.isAssignableFrom(f.getType())
          && ((GenericContainer<?>) f.get(null)).getDockerImageName().contains("redis:");
    } catch (IllegalAccessException ex) {
      return false;
    }
  }
}
