/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.store.pgsql.internal.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.IdOnlyFact;
import org.factcast.store.pgsql.internal.PGConstants;
import org.springframework.jdbc.core.RowMapper;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PGIdFactExtractor implements RowMapper<Fact> {

    final AtomicLong serial;

    @Override
    @NonNull
    public Fact mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
        serial.set(rs.getLong(PGConstants.COLUMN_SER));
        return new IdOnlyFact(UUID.fromString(rs.getString(PGConstants.ALIAS_ID)));
    }
}
