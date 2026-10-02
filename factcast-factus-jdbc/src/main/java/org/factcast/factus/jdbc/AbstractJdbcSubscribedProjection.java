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
package org.factcast.factus.jdbc;

import javax.sql.DataSource;
import lombok.NonNull;
import org.factcast.factus.jdbc.JdbcFactStreamPosition.ProjectionType;
import org.factcast.factus.projection.SubscribedProjection;

public abstract class AbstractJdbcSubscribedProjection extends AbstractJdbcProjection
    implements SubscribedProjection {

  protected AbstractJdbcSubscribedProjection(@NonNull DataSource dataSource) {
    super(dataSource, ProjectionType.SUBSCRIBED);
  }

  protected AbstractJdbcSubscribedProjection(
      @NonNull DataSource dataSource,
      @NonNull String lockTableName,
      @NonNull String positionTableName) {
    super(dataSource, lockTableName, positionTableName);
  }

  protected AbstractJdbcSubscribedProjection(
      @NonNull DataSource dataSource,
      @NonNull JdbcWriterTokenManager writerTokenManager,
      @NonNull JdbcFactStreamPosition factStreamPosition) {
    super(dataSource, writerTokenManager, factStreamPosition);
  }

  /**
   * Whether this instance currently holds the write lock. Use it to gate work that is triggered
   * from outside the fact stream, like a cleanup schedule.
   */
  protected boolean hasLock() {
    return writerTokenManager.hasLock();
  }
}
