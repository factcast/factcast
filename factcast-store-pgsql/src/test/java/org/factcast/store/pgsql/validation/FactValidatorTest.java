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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        when(props.isValidationEnabled()).thenReturn(false);

        FactValidator uut = new FactValidator(props, mock(SchemaRegistry.class));
        Fact probeFact = Fact.builder().ns("foo").type("bar").version(1).buildWithoutPayload();
        assertThat(uut.validate(probeFact)).isEmpty();

    }

    @Test
    public void testFailsToValidateIfNotValidatable() throws Exception {

        PgConfigurationProperties props = mock(PgConfigurationProperties.class);
        when(props.isValidationEnabled()).thenReturn(true);
        when(props.isAllowUnvalidatedPublish()).thenReturn(false);

        FactValidator uut = new FactValidator(props, mock(SchemaRegistry.class));
        Fact probeFact = Fact.builder().ns("foo").type("bar").buildWithoutPayload();
        assertThat(uut.validate(probeFact)).isNotEmpty();

    }

    @Test
    public void testValidateIfNotValidatableButAllowed() throws Exception {

        PgConfigurationProperties props = mock(PgConfigurationProperties.class);
        when(props.isValidationEnabled()).thenReturn(true);
        when(props.isAllowUnvalidatedPublish()).thenReturn(true);

        FactValidator uut = new FactValidator(props, mock(SchemaRegistry.class));
        Fact probeFact = Fact.builder().ns("foo").type("bar").buildWithoutPayload();
        assertThat(uut.validate(probeFact)).isEmpty();

    }

    @Test
    public void testFailsToValidateIfValidatableButMissingSchema() throws Exception {

        PgConfigurationProperties props = mock(PgConfigurationProperties.class);
        when(props.isValidationEnabled()).thenReturn(true);
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
        when(props.isValidationEnabled()).thenReturn(true);
        when(props.isAllowUnvalidatedPublish()).thenReturn(false);

        SchemaRegistry sr = mock(SchemaRegistry.class);
        String schemaJson = "\n" + "{\n" + "  \"additionalProperties\" : false,\n"
                + "  \"properties\" : {\n"
                + "    \"firstName\" : {\n" + "      \"type\": \"string\"\n" + "    }\n" + "  },\n"
                + "  \"required\": [\"firstName\"]\n" + "}";

        JsonSchema schema = ValidationConstants.factory
                .getJsonSchema(ValidationConstants.objectMapper.readTree(schemaJson));
        when(sr.get(Mockito.any())).thenReturn(Optional.of(schema));

        FactValidator uut = new FactValidator(props, sr);
        Fact probeFact = Fact.builder()
                .ns("foo")
                .type("bar")
                .version(1)
                .build("{\"firstName\":\"Peter\"}");
        assertThat(uut.validate(probeFact)).isEmpty();

    }

    @Test
    public void testValidateWithoutMatchingSchema() throws Exception {

        PgConfigurationProperties props = mock(PgConfigurationProperties.class);
        when(props.isValidationEnabled()).thenReturn(true);
        when(props.isAllowUnvalidatedPublish()).thenReturn(true);

        SchemaRegistry sr = mock(SchemaRegistry.class);
        when(sr.get(Mockito.any())).thenReturn(Optional.empty());

        FactValidator uut = new FactValidator(props, sr);
        Fact probeFact = Fact.builder()
                .ns("foo")
                .type("bar")
                .version(1)
                .build("{\"firstName\":\"Peter\"}");
        assertThat(uut.validate(probeFact)).isEmpty();

    }

    @Test
    public void testFailsToValidateWithMatchingSchemaButNonMatchingFact() throws Exception {

        PgConfigurationProperties props = mock(PgConfigurationProperties.class);
        when(props.isValidationEnabled()).thenReturn(true);
        when(props.isAllowUnvalidatedPublish()).thenReturn(false);

        SchemaRegistry sr = mock(SchemaRegistry.class);
        String schemaJson = "\n" + "{\n" + "  \"additionalProperties\" : false,\n"
                + "  \"properties\" : {\n"
                + "    \"firstName\" : {\n" + "      \"type\": \"string\"\n" + "    }\n" + "  },\n"
                + "  \"required\": [\"firstName\"]\n" + "}";

        JsonSchema schema = ValidationConstants.factory
                .getJsonSchema(ValidationConstants.objectMapper.readTree(schemaJson));
        when(sr.get(Mockito.any())).thenReturn(Optional.of(schema));

        FactValidator uut = new FactValidator(props, sr);
        Fact probeFact = Fact.builder().ns("foo").type("bar").version(1).build("{}");
        assertThat(uut.validate(probeFact)).isNotEmpty();

    }

    @Test
    public void testIsValidateable() throws Exception {
        Fact validFact = Fact.builder().ns("ns").type("type").version(1).buildWithoutPayload();
        assertThat(FactValidator.isValidateable(validFact)).isTrue();
    }

    @Test
    public void testIsValidateableWithoutType() throws Exception {
        Fact invalidFact = Fact.builder().ns("ns").version(1).buildWithoutPayload();
        assertThat(FactValidator.isValidateable(invalidFact)).isFalse();
    }

    @Test
    public void testIsValidateableWithoutVersion() throws Exception {
        Fact invalidFact = Fact.builder().ns("ns").type("type").buildWithoutPayload();
        assertThat(FactValidator.isValidateable(invalidFact)).isFalse();
    }
}
