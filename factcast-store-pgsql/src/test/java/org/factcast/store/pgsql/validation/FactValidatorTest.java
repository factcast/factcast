package org.factcast.store.pgsql.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.factcast.core.Fact;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.validation.http.ValidationConstants;
import org.factcast.store.pgsql.validation.schema.SchemaRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.github.fge.jsonschema.main.JsonSchema;

public class FactValidatorTest {

	@Test
	public void testValidateIfDisabled() throws Exception {

		PgConfigurationProperties props = mock(PgConfigurationProperties.class);
		when(props.isValidationEnanbled()).thenReturn(false);

		FactValidator uut = new FactValidator(props, mock(SchemaRegistry.class));
		Fact probeFact = Fact.builder().ns("foo").type("bar").version(1).buildWithoutPayload();
		assertThat(uut.validate(probeFact)).isEmpty();

	}

	@Test
	public void testFailsToValidateIfNotValidatable() throws Exception {

		PgConfigurationProperties props = mock(PgConfigurationProperties.class);
		when(props.isValidationEnanbled()).thenReturn(true);
		when(props.isAllowUnvalidatedPublish()).thenReturn(false);

		FactValidator uut = new FactValidator(props, mock(SchemaRegistry.class));
		Fact probeFact = Fact.builder().ns("foo").type("bar").buildWithoutPayload();
		assertThat(uut.validate(probeFact)).isNotEmpty();

	}

	@Test
	public void testValidateIfNotValidatableButAllowed() throws Exception {

		PgConfigurationProperties props = mock(PgConfigurationProperties.class);
		when(props.isValidationEnanbled()).thenReturn(true);
		when(props.isAllowUnvalidatedPublish()).thenReturn(true);

		FactValidator uut = new FactValidator(props, mock(SchemaRegistry.class));
		Fact probeFact = Fact.builder().ns("foo").type("bar").buildWithoutPayload();
		assertThat(uut.validate(probeFact)).isEmpty();

	}

	@Test
	public void testFailsToValidateIfValidatableButMissingSchema() throws Exception {

		PgConfigurationProperties props = mock(PgConfigurationProperties.class);
		when(props.isValidationEnanbled()).thenReturn(true);
		when(props.isAllowUnvalidatedPublish()).thenReturn(false);
		SchemaRegistry sr = mock(SchemaRegistry.class);
		when(sr.get(Mockito.any())).thenReturn(Optional.empty());

		FactValidator uut = new FactValidator(props, sr);
		Fact probeFact = Fact.builder().ns("foo").type("bar").version(8).buildWithoutPayload();
		assertThat(uut.validate(probeFact)).isNotEmpty();

	}

	@Test
	public void testValidateWithMatchingSchema() throws Exception {

		PgConfigurationProperties props = mock(PgConfigurationProperties.class);
		when(props.isValidationEnanbled()).thenReturn(true);
		when(props.isAllowUnvalidatedPublish()).thenReturn(false);

		SchemaRegistry sr = mock(SchemaRegistry.class);
		String schemaJson = "\n" + "{\n" + "  \"additionalProperties\" : false,\n" + "  \"properties\" : {\n"
				+ "    \"firstName\" : {\n" + "      \"type\": \"string\"\n" + "    }\n" + "  },\n"
				+ "  \"required\": [\"firstName\"]\n" + "}";

		JsonSchema schema = ValidationConstants.factory
				.getJsonSchema(ValidationConstants.objectMapper.readTree(schemaJson));
		when(sr.get(Mockito.any())).thenReturn(Optional.of(schema));

		FactValidator uut = new FactValidator(props, sr);
		Fact probeFact = Fact.builder().ns("foo").type("bar").version(1).build("{\"firstName\":\"Peter\"}");
		assertThat(uut.validate(probeFact)).isEmpty();

	}

	@Test
	public void testValidateWithoutMatchingSchema() throws Exception {

		PgConfigurationProperties props = mock(PgConfigurationProperties.class);
		when(props.isValidationEnanbled()).thenReturn(true);
		when(props.isAllowUnvalidatedPublish()).thenReturn(true);

		SchemaRegistry sr = mock(SchemaRegistry.class);
		when(sr.get(Mockito.any())).thenReturn(Optional.empty());

		FactValidator uut = new FactValidator(props, sr);
		Fact probeFact = Fact.builder().ns("foo").type("bar").version(1).build("{\"firstName\":\"Peter\"}");
		assertThat(uut.validate(probeFact)).isEmpty();

	}

	@Test
	public void testFailsToValidateWithMatchingSchemaButNonMatchingFact() throws Exception {

		PgConfigurationProperties props = mock(PgConfigurationProperties.class);
		when(props.isValidationEnanbled()).thenReturn(true);
		when(props.isAllowUnvalidatedPublish()).thenReturn(false);

		SchemaRegistry sr = mock(SchemaRegistry.class);
		String schemaJson = "\n" + "{\n" + "  \"additionalProperties\" : false,\n" + "  \"properties\" : {\n"
				+ "    \"firstName\" : {\n" + "      \"type\": \"string\"\n" + "    }\n" + "  },\n"
				+ "  \"required\": [\"firstName\"]\n" + "}";

		JsonSchema schema = ValidationConstants.factory
				.getJsonSchema(ValidationConstants.objectMapper.readTree(schemaJson));
		when(sr.get(Mockito.any())).thenReturn(Optional.of(schema));

		FactValidator uut = new FactValidator(props, sr);
		Fact probeFact = Fact.builder().ns("foo").type("bar").version(1).build("{}");
		assertThat(uut.validate(probeFact)).isNotEmpty();

	}
}
