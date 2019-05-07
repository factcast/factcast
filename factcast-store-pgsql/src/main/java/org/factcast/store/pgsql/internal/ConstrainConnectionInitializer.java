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
package org.factcast.store.pgsql.internal;

import javax.sql.DataSource;

import org.springframework.beans.factory.SmartInitializingSingleton;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConstrainConnectionInitializer implements SmartInitializingSingleton {

    private final DataSource ds;

    @Override
    public void afterSingletonsInstantiated() {
        if (ds instanceof org.apache.tomcat.jdbc.pool.DataSource) {

            org.apache.tomcat.jdbc.pool.DataSource tomcatDS = (org.apache.tomcat.jdbc.pool.DataSource) ds;

            // nasty trick to init all connections with limited privileges
            tomcatDS.setInitSQL(
                    "SET SESSION SESSION AUTHORIZATION 'factcastApplicationUser'");

            // and purge all the current/prepared ones, as we are late to the
            // party...
            tomcatDS.purge();

            // TODO we need to find a way, to make it impossible for the user to
            // reset the session authorization

            tomcatDS.setLogValidationErrors(true);

        } else {
            throw new IllegalStateException(
                    "Currently only works with tomcat-jdbc-pool, but DataSource provided is of type "
                            + ds.getClass().getCanonicalName());
        }

    }

}
