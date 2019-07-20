/*
 * Copyright © 2019 factcast
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
package org.factcast.store.pgsql.internal.catchup.paged;

import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.internal.PgPostQueryMatcher;
import org.factcast.store.pgsql.internal.catchup.PgCatchupFactory;
import org.factcast.store.pgsql.internal.query.PgFactIdToSerialMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.Generated;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
// no code in here, just generated @nonnull checks
@Generated
public class PgPagedCatchUpFactory implements PgCatchupFactory {

    @NonNull
    final JdbcTemplate jdbc;

    @NonNull
    final PgConfigurationProperties props;

    @NonNull
    final PgFactIdToSerialMapper serMapper;

    public PgPagedCatchup create(@NonNull SubscriptionRequestTO request,
            @NonNull PgPostQueryMatcher postQueryMatcher,
            @NonNull SubscriptionImpl<Fact> subscription, @NonNull AtomicLong serial) {
        return new PgPagedCatchup(jdbc, props, serMapper, request, postQueryMatcher, subscription,
                serial);
    }
}
