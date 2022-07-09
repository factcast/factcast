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
package org.factcast.script.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.NonNull;
import lombok.Value;

public abstract class Argument<E> {

  public static Argument of(boolean b) {
    return new ValueArgument(b);
  }

  public static Argument of(int i) {
    return new ValueArgument(i);
  }

  public static Argument of(double d) {
    return new ValueArgument(d);
  }

  public static Argument of(long l) {
    return new ValueArgument(l);
  }

  public static Argument of(String s) {
    return new ValueArgument(s);
  }

  public static Argument of(Object o) {
    ObjectMapper om = new ObjectMapper();
    return new ValueArgument(om.convertValue(o, new TypeReference<Map<String, Object>>() {}));
  }

  public static Argument byReference(@NonNull Map<String, Object> map) {
    return new ReferenceArgument(map);
  }

  public abstract E get();

  @Value
  private static class ValueArgument<T> extends Argument<T> {
    T value;

    @Override
    public T get() {
      return value;
    }
  }

  @Value
  private static class ReferenceArgument extends Argument<Map<String, Object>> {
    Map<String, Object> value;

    @Override
    public Map<String, Object> get() {
      return value;
    }
  }
}
