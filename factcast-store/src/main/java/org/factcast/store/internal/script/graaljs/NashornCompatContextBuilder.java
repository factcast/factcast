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
package org.factcast.store.internal.script.graaljs;

import java.util.*;
import lombok.experimental.UtilityClass;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

@UtilityClass
public class NashornCompatContextBuilder {

  static {
    // we ignore this because we're not running on graal and its somehow expected
    System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
  }

  static final Context.Builder CTX =
      Context.newBuilder("js")
          .allowExperimentalOptions(true)
          .option("js.syntax-extensions", "true")
          .option("js.load", "true")
          .option("js.print", "true")
          .option("js.global-arguments", "true")
          .allowAllAccess(true)
          .allowHostAccess(createHostAccess());

  private HostAccess createHostAccess() {
    HostAccess.Builder b = HostAccess.newBuilder(HostAccess.ALL);
    // Last resort conversions similar to those in NashornBottomLinker.
    b.targetTypeMapping(
        Value.class,
        String.class,
        v -> !v.isNull(),
        NashornCompatContextBuilder::toString,
        HostAccess.TargetMappingPrecedence.LOWEST);
    b.targetTypeMapping(
        Number.class,
        Integer.class,
        n -> true,
        Number::intValue,
        HostAccess.TargetMappingPrecedence.LOWEST);
    b.targetTypeMapping(
        Number.class,
        Double.class,
        n -> true,
        Number::doubleValue,
        HostAccess.TargetMappingPrecedence.LOWEST);
    b.targetTypeMapping(
        Number.class,
        Long.class,
        n -> true,
        Number::longValue,
        HostAccess.TargetMappingPrecedence.LOWEST);
    b.targetTypeMapping(
        Number.class,
        Boolean.class,
        n -> true,
        n -> toBoolean(n.doubleValue()),
        HostAccess.TargetMappingPrecedence.LOWEST);
    b.targetTypeMapping(
        String.class,
        Boolean.class,
        n -> true,
        n -> !n.isEmpty(),
        HostAccess.TargetMappingPrecedence.LOWEST);

    // This mapping ensures correct transformation of nested arrays, see #1905
    b.targetTypeMapping(
        // for any conversion Any Value -> Object
        Value.class,
        Object.class,
        // if the value has array elements and members
        (v) -> v.hasArrayElements() && v.hasMembers(),
        // convert to List (instead of Map)
        (v) -> v.as(List.class));
    return b.build();
  }

  private String toString(Value value) {
    return toPrimitive(value).toString();
  }

  // "Type(result) is not Object" heuristic for the purpose of ToPrimitive() conversion
  private boolean isPrimitive(Value value) {
    return value.isString() || value.isNumber() || value.isBoolean() || value.isNull();
  }

  // ToPrimitive()/OrdinaryToPrimitive() operation
  private Value toPrimitive(Value value) {
    if (value.hasMembers()) {
      for (String methodName : new String[] {"toString", "valueOf"}) {
        if (value.canInvokeMember(methodName)) {
          Value maybePrimitive = value.invokeMember(methodName);
          if (isPrimitive(maybePrimitive)) {
            return maybePrimitive;
          }
        }
      }
    }
    if (isPrimitive(value)) {
      return value;
    } else {
      throw new ClassCastException();
    }
  }

  private boolean toBoolean(double d) {
    return d != 0.0 && !Double.isNaN(d);
  }
}
