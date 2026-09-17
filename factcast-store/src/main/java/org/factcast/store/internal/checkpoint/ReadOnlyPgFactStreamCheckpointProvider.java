/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.checkpoint;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.subscription.observer.HighWaterMark;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class ReadOnlyPgFactStreamCheckpointProvider implements FactStreamCheckpointProvider {

  static final String MISSING_CHECKPOINT = "The singleton fact-stream checkpoint row is missing";
  static final String READ_CHECKPOINT =
      "SELECT fact_ser, fact_id, notification_ser FROM factstream_checkpoint WHERE id=1";

  @NonNull private final DataSource primaryDataSource;
  private final AtomicReference<FactStreamCheckpoint> current =
      new AtomicReference<>(FactStreamCheckpoint.empty());

  @Override
  public synchronized @NonNull FactStreamCheckpoint advance() {
    FactStreamCheckpoint checkpoint = read(primaryDataSource);
    current.set(checkpoint);
    return checkpoint;
  }

  @Override
  public @NonNull FactStreamCheckpoint current() {
    return current.get();
  }

  @Override
  public @NonNull FactStreamCheckpoint read(@NonNull DataSource dataSource) {
    List<FactStreamCheckpoint> checkpoints =
        jdbcTemplate(dataSource)
            .query(
                READ_CHECKPOINT,
                (rs, rowNum) ->
                    new FactStreamCheckpoint(
                        HighWaterMark.of(
                            rs.getObject("fact_id", UUID.class), rs.getLong("fact_ser")),
                        rs.getLong("notification_ser")));
    if (checkpoints.isEmpty()) {
      throw new IllegalStateException(MISSING_CHECKPOINT);
    }
    return checkpoints.get(0);
  }

  protected JdbcTemplate jdbcTemplate(@NonNull DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  protected final void updateCurrent(@NonNull FactStreamCheckpoint checkpoint) {
    current.set(checkpoint);
  }
}
