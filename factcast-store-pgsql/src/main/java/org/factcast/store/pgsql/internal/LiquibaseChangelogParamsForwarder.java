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

import java.util.HashMap;
import java.util.Map;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import liquibase.integration.spring.SpringLiquibase;
import lombok.NonNull;

public class LiquibaseChangelogParamsForwarder implements BeanPostProcessor {

    private final DataSource dataSource;

    public LiquibaseChangelogParamsForwarder(@NonNull javax.sql.DataSource dataSource) {

        this.dataSource = getTomcatDataSource(dataSource);
    }

    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName)
            throws BeansException {

        if (bean instanceof SpringLiquibase) {
            SpringLiquibase liquibase = ((SpringLiquibase) bean);

            PoolConfiguration poolProperties = dataSource.getPoolProperties();

            Map<String, String> params = new HashMap<String, String>();
            params.put("dml-user", poolProperties.getUsername());
            params.put("dml-user-pw", poolProperties.getPassword());
            liquibase.setChangeLogParameters(params);

        }

        return bean;
    }

    private DataSource getTomcatDataSource(javax.sql.DataSource dataSource) {

        if (DataSource.class.isAssignableFrom(dataSource.getClass())) {
            return (DataSource) dataSource;
        } else {
            throw new IllegalArgumentException("expected "
                    + DataSource.class.getName()
                    + " , but got " + dataSource.getClass().getName());
        }
    }

}
