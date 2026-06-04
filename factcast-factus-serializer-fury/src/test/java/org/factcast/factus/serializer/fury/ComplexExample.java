/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.factus.serializer.fury;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.collect.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.fury.*;
import org.factcast.core.util.FactCastJson;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@EqualsAndHashCode
public class ComplexExample {
  boolean b = true;
  short s = 12;
  int i = 623517;
  double d = 0.872345763d;
  long l = 1273;
  char c = 'x';
  String txt = "narf";

  List<Sub> list = Lists.newArrayList(new Sub());
  Set<Sub> set = Sets.newHashSet(new Sub());
  Map<UUID, Sub> map = new HashMap<>();
  BigDecimal bd = new BigDecimal("0.7235481762346872364823468");

  ComplexExample() {
    map.put(UUID.randomUUID(), new Sub());
  }

  @NoArgsConstructor
  @EqualsAndHashCode
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  static class Sub {
    UUID uuid = UUID.randomUUID();
  }

  String toJson() {
    return FactCastJson.writeValueAsString(this);
  }

  String toFury() {
    ThreadSafeFury fury = Fury.builder().requireClassRegistration(false).buildThreadSafeFury();
    byte[] serialize = fury.serialize(this);
    return Base64.getEncoder().encodeToString(serialize);
  }

  public static void main(String[] args) {
    ComplexExample complexExample = new ComplexExample();
    System.out.println(complexExample.toJson());
    System.out.println(complexExample.toFury());
  }
}
