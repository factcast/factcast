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
package org.factcast.store.registry.transformation.store;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
public class PgTransformationStoreImpl extends AbstractTransformationStore {
  @NonNull private final JdbcTemplate jdbcTemplate;

  @NonNull private final TransactionTemplate txTemplate;

  @NonNull private final RegistryMetrics registryMetrics;

  @Override
  protected void doStore(@NonNull TransformationSource source, String transformation)
      throws TransformationConflictException {

    // transformationstore allows transformations to be updated (use with extreme care)
    txTemplate.execute(
        ts -> {
          // delete all duplicates regarding (ns,type,from,to)
          // note: the id might have changed, so that ON CONFLICT becomes fiddly.
          jdbcTemplate.update(
              "DELETE FROM transformationstore WHERE ns=? AND type=? AND from_version=? AND to_version=?",
              source.ns(),
              source.type(),
              source.from(),
              source.to());

          jdbcTemplate.update(
              "INSERT INTO transformationstore (id, hash, ns, type, from_version, to_version,"
                  + " transformation) VALUES (?,?,?,?,?,?,?)",
              source.id(),
              source.hash(),
              source.ns(),
              source.type(),
              source.from(),
              source.to(),
              transformation);
          return null;
        });
  }

  @Override
  public boolean contains(@NonNull TransformationSource source)
      throws TransformationConflictException {
    List<String> hashes =
        jdbcTemplate.queryForList(
            "SELECT hash FROM transformationstore WHERE id=?", String.class, source.id());

    if (!hashes.isEmpty()) {
      String hash = hashes.get(0);
      if (hash.equals(source.hash())) {
        return true;
      } else {
        registryMetrics.count(
            RegistryMetrics.EVENT.TRANSFORMATION_CONFLICT,
            Tags.of(Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, source.id())));

        throw new TransformationConflictException(
            "Source at " + source + " does not match the stored hash " + hash);
      }
    } else {
      return false;
    }
  }

  @Override
  public List<Transformation> get(@NonNull TransformationKey key) {
    return jdbcTemplate.query(
        "SELECT from_version, to_version, transformation FROM transformationstore WHERE ns=? AND"
            + " type=?",
        new Object[] {key.ns(), key.type()},
        (rs, rowNum) -> {
          int from = rs.getInt("from_version");
          int to = rs.getInt("to_version");
          String code = rs.getString("transformation");

          return new SingleTransformation(key, from, to, Optional.ofNullable(code));
        });
  }
}
