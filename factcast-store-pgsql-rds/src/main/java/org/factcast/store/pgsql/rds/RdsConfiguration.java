package org.factcast.store.pgsql.rds;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RdsConfiguration {

    @Bean
    RdsDataSourceFactorBeanPostProcessor rdsDataSourceFactorBeanPostProcessor() {
        return new RdsDataSourceFactorBeanPostProcessor();
    }

}
