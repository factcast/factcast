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
package org.factcast.store.pgsql.registry.validation;

import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.registry.SchemaRegistry;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;
import org.factcast.store.pgsql.registry.validation.schema.store.InMemSchemaStoreImpl;
import org.factcast.store.pgsql.registry.validation.schema.store.PgSchemaStoreImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import liquibase.integration.spring.SpringLiquibase;
import lombok.NonNull;

@Configuration
public class FactValidatorConfiguration {
    @Bean
    public SchemaStore schemaStore(@NonNull JdbcTemplate jdbcTemplate,
            @NonNull PgConfigurationProperties props, @Autowired(
                    required = false) SpringLiquibase unused) {
        if (props.isValidationEnabled() && props.isPersistentSchemaStore())
            return new PgSchemaStoreImpl(jdbcTemplate, unused);

        // otherwise
        return new InMemSchemaStoreImpl();
    }

    @Bean
    public FactValidator factValidator(PgConfigurationProperties props, SchemaRegistry registry) {
        if (props.isValidationEnabled())
            return new FactValidator(props, registry);
        else
            return null;
    }

    @Bean
    public FactValidationAspect factValidationAspect(PgConfigurationProperties props,
            FactValidator v) {
        if (props.isValidationEnabled()) {
            return new FactValidationAspect(v);
        } else
            return null;
    }
}
