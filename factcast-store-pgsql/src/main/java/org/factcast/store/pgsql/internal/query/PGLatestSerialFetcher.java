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
package org.factcast.store.pgsql.internal.query;

import org.factcast.store.pgsql.internal.PGConstants;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Component;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Fetches the latest SERIAL from the fact table.
 *
 * @author uwe.schaefer@mercateo.com
 */
@RequiredArgsConstructor
@Component
public class PGLatestSerialFetcher {

    @NonNull
    final JdbcTemplate jdbcTemplate;

    /**
     * @return 0, if no Fact is found,
     */
    public long retrieveLatestSer() {
        try {
            SqlRowSet rs = jdbcTemplate.queryForRowSet(PGConstants.SELECT_LATEST_SER);
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (EmptyResultDataAccessException ignored) {
        }
        return 0;
    }
}
