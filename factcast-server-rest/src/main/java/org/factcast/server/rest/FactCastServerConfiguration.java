package org.factcast.server.rest;

import org.factcast.core.store.FactStore;
import org.factcast.store.inmem.InMemFactStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mercateo.common.rest.schemagen.JsonHyperSchemaCreator;
import com.mercateo.common.rest.schemagen.JsonSchemaGenerator;
import com.mercateo.common.rest.schemagen.RestJsonSchemaGenerator;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchemaCreator;
import com.mercateo.common.rest.schemagen.types.PaginatedResponseBuilderCreator;

@Configuration
public class FactCastServerConfiguration {

	private InMemFactStore inMemFactStore = new InMemFactStore();

	@Bean
	public JsonSchemaGenerator jsonSchemaGenerator() {
		return new RestJsonSchemaGenerator();
	}

	@Bean
	JsonHyperSchemaCreator jsonHyperSchemaCreator() {
		return new JsonHyperSchemaCreator();
	}

	@Bean
	ObjectWithSchemaCreator objectWithSchemaCreator() {
		return new ObjectWithSchemaCreator();
	}

	@Bean
	PaginatedResponseBuilderCreator paginatedResponseBuilderCreator() {
		return new PaginatedResponseBuilderCreator();
	}

	@Bean
	FactStore getFactStore() {
		return inMemFactStore;
	}
}