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
package org.factcast.store.pgsql.validation;

import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.validation.http.HttpSchemaRegistry;
import org.factcast.store.pgsql.validation.http.IndexFetcher;
import org.factcast.store.pgsql.validation.schema.SchemaRegistry;
import org.factcast.store.pgsql.validation.schema.SchemaStore;
import org.factcast.store.pgsql.validation.schema.store.InMemSchemaStoreImpl;
import org.factcast.store.pgsql.validation.schema.store.PgSchemaStoreImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class FactValidatorConfiguration {

    @Bean
    public SchemaRegistry schemaRegistry(PgConfigurationProperties p,

            @NonNull SchemaStore store) {

        if (p.isValidationEnanbled()) {
            HttpSchemaRegistry httpSchemaRegistry = new HttpSchemaRegistry(p.getSchemaRegistryUrl(),
                    store);
            httpSchemaRegistry.refresh();
            return httpSchemaRegistry;
        } else {
            log.warn(
                    "**** SchemaRegistry-mode is disabled. Fact validation will not happen. This is discouraged for production environments. You have been warned. ****");
            return new NOPSchemaRegistry();
        }

    }

    @Bean
    public IndexFetcher indexFetcher(PgConfigurationProperties p) {
        return new IndexFetcher(p.getSchemaRegistryUrl());
    }

    @Bean
    public SchemaStore schemaStore(@NonNull JdbcTemplate jdbcTemplate,
            @NonNull PgConfigurationProperties props) {
        if (props.isValidationEnanbled() && props.isPersistentSchemaStore())
            return new PgSchemaStoreImpl(jdbcTemplate);

        // otherwise
        return new InMemSchemaStoreImpl();
    }

    @Bean
    public FactValidator factValidation(PgConfigurationProperties props, SchemaRegistry registry) {
        return new FactValidator(props, registry);
    }

    @Bean
    public FactValidationAspect factValidationAspect(PgConfigurationProperties props,
            FactValidator v) {
        if (props.isValidationEnanbled()) {
            return new FactValidationAspect(v);
        } else
            return null;
    }
}
