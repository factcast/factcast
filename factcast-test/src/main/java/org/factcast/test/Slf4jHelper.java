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
import java.lang.reflect.Modifier;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import slf4jtest.Settings;
import slf4jtest.TestLogger;
import slf4jtest.TestLoggerFactory;

@Value
public class Slf4jHelper {
  @SneakyThrows
  public static TestLogger replaceLogger(@NonNull Object instance) {
    Class<?> clazz = instance.getClass();
    Field field = clazz.getDeclaredField("log");
    field.setAccessible(true);

    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

    TestLogger logger = new TestLoggerFactory(new Settings().enableAll()).getLogger(clazz);
    field.set(null, logger);
    return logger;
  }
}
