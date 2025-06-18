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
package org.factcast.core.spec;

import lombok.NonNull;
import lombok.Value;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.event.Specification;

@Value
@Slf4j
@With
public class FactSpecCoordinates {

  FactSpecCoordinates(String ns, String type, int version) {
    if (ns.trim().isEmpty()) {
      throw new IllegalArgumentException("Namespace must not be empty");
    }

    this.ns = ns;
    this.type = type;
    this.version = version;
  }

  String ns;

  String type;

  int version;

  public static FactSpecCoordinates from(@NonNull FactSpec fs) {
    return new FactSpecCoordinates(fs.ns(), fs.type(), fs.version());
  }

  public static FactSpecCoordinates from(@NonNull Fact fact) {
    return new FactSpecCoordinates(fact.ns(), fact.type(), fact.version());
  }

  public static FactSpecCoordinates from(Class<?> clazz) {

    String defaultType = clazz.getSimpleName();

    Specification spec = clazz.getAnnotation(Specification.class);
    if (spec == null) {
      throw new IllegalArgumentException(
          "@" + Specification.class.getSimpleName() + " missing on " + clazz);
    }

    String _ns = spec.ns();
    if (_ns.trim().isEmpty()) {
      throw new IllegalArgumentException("Empty namespace encountered on class " + clazz);
    }

    String _type = spec.type();
    if (_type.trim().isEmpty()) {
      _type = defaultType;
    }

    int version = spec.version();

    return new FactSpecCoordinates(_ns, _type, version);
  }

  @SuppressWarnings("java:S1066")
  public boolean matches(@NonNull FactSpecCoordinates key) {
    if (key.ns.equals(ns) || key.ns.equals("*") || ns.equals("*")) {
      if (key.type.equals(type) || key.type.equals("*") || type.equals("*")) {
        if (key.version == version || key.version == 0 || version == 0) {
          return true;
        }
      }
    }

    return false;
  }
}
