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
package org.factcast.itests.factus;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.factcast.factus.Handler;
import org.factcast.factus.projection.ManagedProjection;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JpaUserNames extends ManagedProjection {

    final JdbcTemplate tpl;

    // TODO: @HandlerFor()//BANG!

    class MyHandlers {

        @Handler
        void apply(UserCreated created) {
            tpl.update("INSERT INTO usernames VALUES(?,?)", ps -> {
                ps.setObject(1, created.aggregateId());
                ps.setString(2, created.userName());
            });
        }

        ;

        @Handler
        void apply(UserDeleted deleted) {
            tpl.update("DELETE FROM usernames WHERE id=?", ps -> {
                ps.setObject(1, deleted.aggregateId());
            });
        }

        ;
    }

    void onException(Exception e) {

    }

    int count() {
        return tpl.queryForObject("SELECT count(*) FROM usernames", Integer.class);
    }

    boolean contains(String name) {
        return tpl.queryForObject("SELECT count(*) FROM usernames WHERE name=?", Integer.class,
                name) > 0;
    }

    Set<UUID> allUserIdsForDeletingInTest() {
        return new HashSet<>(tpl.queryForList("SELECT id FROM usernames", UUID.class));
    }

    // JPA based impl of ManagedProjection:

    @Override
    public AutoCloseable aquireWriteToken(Duration maxWait) {
        // TODO probably use a dedicated table for locks
        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
                // TODO
            }
        };
    }

    public UUID state() {
        try {
            return tpl.queryForObject("SELECT state FROM usernames_state ORDER BY id DESC LIMIT 1",
                    UUID.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public void state(@NonNull UUID state) {
        tpl.update("INSERT INTO usernames_state(state) VALUES(?)", state);
    }

}
