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
package org.factcast.store.internal.script;

import java.util.*;
import java.util.function.*;

import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.NonNull;
import lombok.Value;

public interface JSArgument<E> extends Supplier<E> {

  static JSArgument<Boolean> byValue(@NonNull boolean b) {
    return new ValueArgument<>(b);
  }

  static JSArgument<Boolean> ofNull() {
    return () -> null;
  }

  static JSArgument<Integer> byValue(@NonNull int i) {
    return new ValueArgument<>(i);
  }

  static JSArgument<Double> byValue(@NonNull double d) {
    return new ValueArgument<>(d);
  }

  static JSArgument<Long> byValue(@NonNull long l) {
    return new ValueArgument<>(l);
  }

  static JSArgument<String> byValue(@NonNull String s) {
    return new ValueArgument<>(s);
  }

  @SuppressWarnings("unchecked")
  static JSArgument<Map<String, Object>> byValue(@NonNull JsonNode o) {
    return new ValueArgument<>((Map<String, Object>) FactCastJson.convertValue(o, Map.class));
  }

  static JSArgument<Map<String, Object>> byValue(@NonNull Map<String, Object> o) {
    return new ValueArgument<>(o);
  }

  static JSArgument<Map<String, Object>> byReference(@NonNull Map<String, Object> map) {
    return new ReferenceArgument(map);
  }

  @Value
  class ValueArgument<T> implements JSArgument<T> {
    T value;

    @Override
    public T get() {
      return value;
    }
  }

  @Value
  class ReferenceArgument implements JSArgument<Map<String, Object>> {
    Map<String, Object> value;

    @Override
    public Map<String, Object> get() {
      return value;
    }
  }
}
