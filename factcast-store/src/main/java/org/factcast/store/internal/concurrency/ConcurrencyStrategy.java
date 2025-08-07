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
package org.factcast.store.internal.concurrency;

import com.google.common.collect.Lists;
import java.sql.*;
import java.util.*;
import java.util.function.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.store.internal.PgConstants;
import org.jetbrains.annotations.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.*;

@Slf4j
@RequiredArgsConstructor
public abstract class ConcurrencyStrategy {
  protected final JdbcTemplate jdbcTemplate;

  public abstract void publish(@NonNull List<? extends Fact> factsToPublish);

  @SuppressWarnings("java:S4276")
  public abstract boolean publishIfUnchanged(
      @NonNull List<? extends Fact> factsToPublish, @NonNull Predicate<Long> isUnchanged);

  /**
   * @param factsToPublish
   * @return
   */
  protected long batchInsertFacts(@NotNull List<? extends Fact> factsToPublish) {

    if (factsToPublish.isEmpty()) {
      throw new IllegalArgumentException("Nothing to insert");
    }

    ArrayList<? extends Fact> facts = Lists.newArrayList(factsToPublish);

    int numberOfFactsToPublish = facts.size();
    log.trace("Inserting {} fact(s)", numberOfFactsToPublish);

    KeyHolder keys = new GeneratedKeyHolder();
    PreparedStatementCreatorFactory f =
        new PreparedStatementCreatorFactory(PgConstants.INSERT_FACT);
    // this is surprisingly hard using spring
    f.setReturnGeneratedKeys(true);
    f.setGeneratedKeysColumnNames(PgConstants.COLUMN_SER);
    PreparedStatementCreator psc = f.newPreparedStatementCreator(Collections.emptyList());

    jdbcTemplate.batchUpdate(
        psc,
        new BatchPreparedStatementSetter() {

          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            Fact fact = facts.get(i);
            ps.setString(1, fact.jsonHeader());
            ps.setString(2, fact.jsonPayload());
          }

          @Override
          public int getBatchSize() {
            return facts.size();
          }
        },
        keys);

    if (facts.size() > 1) {
      //noinspection OptionalGetWithoutIsPresent
      return keys.getKeyList().stream().mapToLong(m -> (long) m.get("ser")).min().getAsLong();
    } else {
      //noinspection DataFlowIssue
      return keys.getKeyAs(Long.class);
    }
  }
}
