/*
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
package org.factcast.store.pgsql.internal.lock;

import java.sql.Connection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class AdvisoryWriteLock implements FactTableWriteLock {
    private final JdbcTemplate tpl;

    @Override
    @SneakyThrows
    @Transactional(propagation = Propagation.MANDATORY)
    public void aquireExclusiveLock() {
        Connection c = DataSourceUtils.getConnection(tpl.getDataSource());
        c.prepareCall("SELECT pg_advisory_lock(129)").execute();

    }

    @Override
    @SneakyThrows
    @Transactional(propagation = Propagation.MANDATORY)
    public void release() {
        Connection c = DataSourceUtils.getConnection(tpl.getDataSource());
        c.prepareCall("SELECT pg_advisory_unlock(129)").execute();
    }

}
