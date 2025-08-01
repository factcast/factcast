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
package org.factcast.store.internal.filter;

import java.util.*;
import lombok.*;
import org.factcast.core.Fact;
import org.factcast.core.spec.*;
import org.factcast.store.internal.PgFact;

/**
 * still needs to be used in order to be able to filter for all aspects of single factspec (Basic1
 * AND JS1 AND AggIdProp1) OR (Basic2 AND JS2 AND AggIdProp2)
 *
 * <p>There is no way to optimize this away.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public final class BasicMatcher implements PGFactMatcher {

  @NonNull final String ns;

  final Integer version;

  final String type;

  final Set<UUID> aggIds;

  final Map<String, String> meta;

  final Map<String, Boolean> metaKeyExists;

  public BasicMatcher(@NonNull FactSpec spec) {
    // opt: prevent method calls by prefetching to final fields.
    // yes, they might be inlined at some point, but making decisions based
    // on final fields should help.
    //
    // this Predicate is pretty performance critical
    ns = spec.ns();
    type = spec.type();
    version = spec.version();
    aggIds = spec.aggIds();
    meta = spec.meta();
    metaKeyExists = spec.metaKeyExists();
  }

  @Override
  public boolean test(PgFact t) {
    boolean match = nsMatch(t);
    match = match && typeMatch(t);
    match = match && versionMatch(t);
    match = match && aggIdMatch(t);
    match = match && metaMatch(t);
    match = match && metaKeyExistsMatch(t);

    return match;
  }

  boolean metaMatch(Fact t) {
    if (meta.isEmpty()) {
      return true;
    }
    return meta.entrySet().stream()
        .allMatch(e -> t.header().meta().getAll(e.getKey()).contains(e.getValue()));
  }

  boolean metaKeyExistsMatch(Fact t) {
    if (metaKeyExists.isEmpty()) {
      return true;
    }
    return metaKeyExists.entrySet().stream()
        .allMatch(
            e -> {
              boolean mustExist = Objects.requireNonNull(e.getValue());
              String metaValue = t.header().meta().getFirst(e.getKey());
              return (mustExist && metaValue != null) || (!mustExist && metaValue == null);
            });
  }

  boolean nsMatch(Fact t) {
    return ns.equals(t.ns()) || ns.equals("*");
  }

  boolean typeMatch(Fact t) {
    if (type == null) {
      return true;
    }
    String otherType = t.type();
    return type.equals(otherType) || type.equals("*");
  }

  boolean versionMatch(Fact t) {
    if (version == 0) {
      return true;
    }
    Integer otherVersion = t.version();
    return version.equals(otherVersion);
  }

  boolean aggIdMatch(Fact t) {
    if (aggIds == null || aggIds.isEmpty()) {
      return true;
    }
    return t.aggIds().containsAll(aggIds);
  }

  public static BasicMatcher matches(@NonNull FactSpec spec) {
    return new BasicMatcher(spec);
  }
}
