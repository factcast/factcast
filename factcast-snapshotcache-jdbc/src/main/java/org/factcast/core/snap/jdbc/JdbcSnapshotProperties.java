/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.core.snap.jdbc;

import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(fluent = false)
public class JdbcSnapshotProperties {

  public static final String PROPERTIES_PREFIX = "factcast.snapshot.jdbc";

  private String snapshotTableName = "factcast_snapshot";
  private int deleteSnapshotStaleForDays = 90;

  public JdbcSnapshotProperties() {}

  public String getSnapshotTableName() {
    return this.snapshotTableName;
  }

  public int getDeleteSnapshotStaleForDays() {
    return this.deleteSnapshotStaleForDays;
  }

  public JdbcSnapshotProperties setSnapshotTableName(String snapshotTableName) {
    // prevent sql injection
    if (!snapshotTableName.matches("[_a-zA-Z0-9\\.]+"))
      throw new IllegalArgumentException("Suspicious table name defined: " + snapshotTableName);

    this.snapshotTableName = snapshotTableName;
    return this;
  }

  public JdbcSnapshotProperties setDeleteSnapshotStaleForDays(int deleteSnapshotStaleForDays) {
    this.deleteSnapshotStaleForDays = deleteSnapshotStaleForDays;
    return this;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof JdbcSnapshotProperties)) return false;
    final JdbcSnapshotProperties other = (JdbcSnapshotProperties) o;
    if (!other.canEqual((Object) this)) return false;
    final Object this$snapshotTableName = this.getSnapshotTableName();
    final Object other$snapshotTableName = other.getSnapshotTableName();
    if (this$snapshotTableName == null
        ? other$snapshotTableName != null
        : !this$snapshotTableName.equals(other$snapshotTableName)) return false;
    if (this.getDeleteSnapshotStaleForDays() != other.getDeleteSnapshotStaleForDays()) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof JdbcSnapshotProperties;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $snapshotTableName = this.getSnapshotTableName();
    result = result * PRIME + ($snapshotTableName == null ? 43 : $snapshotTableName.hashCode());
    result = result * PRIME + this.getDeleteSnapshotStaleForDays();
    return result;
  }

  public String toString() {
    return "JdbcSnapshotProperties(snapshotTableName="
        + this.getSnapshotTableName()
        + ", deleteSnapshotStaleForDays="
        + this.getDeleteSnapshotStaleForDays()
        + ")";
  }
}
