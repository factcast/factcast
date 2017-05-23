package org.factcast.store.pgsql.rds;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.aws.jdbc.datasource.TomcatJdbcDataSourceFactory;
import org.springframework.cloud.aws.jdbc.rds.AmazonRdsDataSourceFactoryBean;

/**
 * exchange the given TomcatJdbcDataSourceFactory with a customized factory so
 * we can configure the datasource connection pool
 * 
 *
 */

public class RdsDataSourceFactorBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName)
            throws BeansException {

        if (bean instanceof AmazonRdsDataSourceFactoryBean) {
            ((AmazonRdsDataSourceFactoryBean) bean).setDataSourceFactory(
                    tomcatJdbcDataSourceFactory());
        }

        return bean;
    }

    TomcatJdbcDataSourceFactory tomcatJdbcDataSourceFactory() {

        TomcatJdbcDataSourceFactory fac = new TomcatJdbcDataSourceFactory();

        fac.setRemoveAbandonedTimeout(360000);
        fac.setMaxWait(20000);
        return fac;

    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
        return bean;
    }
}
