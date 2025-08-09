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
package org.factcast.store.internal.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.PgFact;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
public class PgFactExtractor implements RowMapper<Fact> {

  final AtomicLong serial;

  @Override
  @NonNull
  public PgFact mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
    serial.set(rs.getLong(PgConstants.COLUMN_SER));
    return PgFact.from(rs);
  }
}
