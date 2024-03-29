/*
 * Copyright © 2017-2022 factcast.org
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
import java.util.*;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode;
import org.springframework.test.context.TestContext;

public interface FactCastIntegrationTestExtension {

  // returns true if successful, false if needed dependency is not yet available
  default boolean initialize() {
    return true;
  }

  // in order to express, why the initialzation went wrong
  default String createUnableToInitializeMessage() {
    return "reason unknown";
  }

  default void prepareContainers(TestContext ctx) {}

  default void wipeExternalDataStore(TestContext ctx) {}

  default void injectFields(TestContext ctx) {}

  default void beforeAll(TestContext ctx) {}

  default void beforeEach(TestContext ctx) {}

  default void afterEach(TestContext ctx) {}

  default void afterAll(TestContext ctx) {}

  static void inject(
      @NonNull Object testInstance, @NonNull Class<?> targetType, @Nullable Object toInject) {
    List<Field> proxyFields =
        ReflectionUtils.findFields(
            testInstance.getClass(),
            f -> targetType.equals(f.getType()),
            HierarchyTraversalMode.BOTTOM_UP);
    proxyFields.forEach(f -> setFieldValue(f, testInstance, toInject));
  }

  static void inject(@NonNull Object testInstance, @NonNull Object toInject) {
    inject(testInstance, toInject.getClass(), toInject);
  }

  @SneakyThrows
  static void setFieldValue(Field f, Object t, Object value) {
    f.setAccessible(true);
    f.set(t, value);
  }
}
