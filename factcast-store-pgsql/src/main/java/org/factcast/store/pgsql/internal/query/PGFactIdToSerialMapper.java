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

import java.util.UUID;

import org.factcast.store.pgsql.internal.PGConstants;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Fetches a SER from a Fact-Id.
 *
 * @author uwe.schaefer@mercateo.com
 */
@Component
@RequiredArgsConstructor
public class PGFactIdToSerialMapper {

    final JdbcTemplate jdbcTemplate;

    /**
     * Fetches the SER of a particular Fact identified by id
     *
     * @param id
     *            the FactId to look for
     * @return the corresponding SER, 0, if no Fact is found for the id given.
     */
    public long retrieve(UUID id) {
        if (id != null) {
            try {
                // throws EmptyResultDataAccessException if is not found!
                return jdbcTemplate.queryForObject(PGConstants.SELECT_BY_HEADER_JSON, new Object[] {
                        "{\"id\":\"" + id + "\"}" }, Long.class);
            } catch (EmptyResultDataAccessException ignored) {
            }
        }
        return 0;
    }
}
