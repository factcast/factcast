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
package org.factcast.store.pgsql.rds;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.aws.jdbc.datasource.TomcatJdbcDataSourceFactory;
import org.springframework.cloud.aws.jdbc.rds.AmazonRdsDataSourceFactoryBean;
import org.springframework.core.env.Environment;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * exchange the given TomcatJdbcDataSourceFactory with a customized factory so
 * we can configure the datasource connection pool
 */
@RequiredArgsConstructor
public class RdsDataSourceFactorBeanPostProcessor implements BeanPostProcessor {

    private final Environment env;

    @Override
    @NonNull
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName)
            throws BeansException {
        if (bean instanceof AmazonRdsDataSourceFactoryBean) {
            ((AmazonRdsDataSourceFactoryBean) bean).setDataSourceFactory(
                    tomcatJdbcDataSourceFactory());
        }
        return bean;
    }

    TomcatJdbcDataSourceFactory tomcatJdbcDataSourceFactory() {
        TomcatJdbcDataSourceFactory fac = new TomcatJdbcDataSourceFactory();
        fac.setTestOnBorrow(env.getProperty("spring.datasource.tomcat.testOnBorrow", Boolean.class,
                true));
        fac.setConnectionProperties(env.getProperty("spring.datasource.tomcat.connectionProperties",
                String.class, "socketTimeout=20;connectTimeout=10;loginTimeout=10"));
        return fac;
    }

    @Override
    @NonNull
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName)
            throws BeansException {
        return bean;
    }
}
