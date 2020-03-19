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
package org.factcast.store.pgsql.registry.transformation;

import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.registry.SchemaRegistry;
import org.factcast.store.pgsql.registry.transformation.chains.TransformationChains;
import org.factcast.store.pgsql.registry.transformation.store.InMemTransformationStoreImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import liquibase.integration.spring.SpringLiquibase;
import lombok.NonNull;

@Configuration
public class TransformationConfiguration {
	@Bean
	public TransformationStore transformationStore(@NonNull JdbcTemplate jdbcTemplate,
			@NonNull PgConfigurationProperties props, @NonNull SpringLiquibase unused) {
		if (props.isValidationEnabled() && props.isPersistentSchemaStore())
			return null;

		// otherwise
		return new InMemTransformationStoreImpl();
	}

	@Bean
	public TransformationChains transformationChains(SchemaRegistry r) {
		return new TransformationChains(r);
	}
}
