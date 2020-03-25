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
package org.factcast.store.pgsql.registry;

import java.net.MalformedURLException;
import java.net.URL;

import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.registry.classpath.ClasspathSchemaRegistry;
import org.factcast.store.pgsql.registry.http.HttpSchemaRegistry;
import org.factcast.store.pgsql.registry.transformation.TransformationConfiguration;
import org.factcast.store.pgsql.registry.transformation.TransformationStore;
import org.factcast.store.pgsql.registry.validation.FactValidatorConfiguration;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableScheduling
@Import({ FactValidatorConfiguration.class, TransformationConfiguration.class })
public class SchemaRegistryConfiguration {

    @Bean
    public SchemaRegistry schemaRegistry(PgConfigurationProperties p,
            @NonNull SchemaStore schemaStore, @NonNull TransformationStore transformationStore) {

        try {

            if (p.isValidationEnabled()) {
                String fullUrl = p.getSchemaRegistryUrl();
                if (!fullUrl.contains(":"))
                    fullUrl = "classpath:" + fullUrl;

                String protocol = fullUrl.substring(0, fullUrl.indexOf(":"));

                if ("http".equals(protocol) || "https".equals(protocol)) {
                    HttpSchemaRegistry httpSchemaRegistry;
                    httpSchemaRegistry = new HttpSchemaRegistry(new URL(fullUrl + "/"),
                            schemaStore, transformationStore);
                    httpSchemaRegistry.fetchInitial();
                    return httpSchemaRegistry;
                }

                if ("classpath".equals(protocol)) {
                    ClasspathSchemaRegistry registry = new ClasspathSchemaRegistry(fullUrl
                            .substring("classpath:".length()),
                            schemaStore, transformationStore);
                    registry.fetchInitial();
                    return registry;
                }

                throw new IllegalArgumentException(
                        "schemaRegistryUrl has an unknown protocol: '" + protocol
                                + "'. Just 'http', 'https' and 'classpath' are allowed");

            } else {
                log.warn(
                        "**** SchemaRegistry-mode is disabled. Fact validation will not happen. This is discouraged for production environments. You have been warned. ****");
                return new NOPSchemaRegistry();
            }

        } catch (MalformedURLException e) {
            throw new SchemaRegistryUnavailableException(e);
        }

    }

    @Bean
    public ScheduledRegistryRefresher scheduledRegistryFresher(SchemaRegistry registry,
            PgConfigurationProperties properties) {
        if (properties.isValidationEnabled()) {
            return new ScheduledRegistryRefresher(registry);
        } else
            return null;
    }
}
