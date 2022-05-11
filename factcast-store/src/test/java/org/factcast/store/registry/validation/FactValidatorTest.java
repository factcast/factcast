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
package org.factcast.store.registry.validation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.networknt.schema.JsonSchema;
import io.micrometer.core.instrument.Tags;
import java.util.Optional;
import org.factcast.core.Fact;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.store.registry.http.ValidationConstants;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics.EVENT;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

public class FactValidatorTest {
  @Test
  public void testSchemaRegistryDisabled() throws Exception {

    StoreConfigurationProperties props = mock(StoreConfigurationProperties.class);
    when(props.isSchemaRegistryConfigured()).thenReturn(false);

    FactValidator uut =
        new FactValidator(props, mock(SchemaRegistry.class), mock(RegistryMetrics.class));
    Fact probeFact = Fact.builder().ns("foo").type("bar").version(1).buildWithoutPayload();
    assertThat(uut.validate(probeFact)).isEmpty();
  }

  @Test
  public void testValidationDisabled() throws Exception {

    StoreConfigurationProperties props = mock(StoreConfigurationProperties.class);
    when(props.isSchemaRegistryConfigured()).thenReturn(true);
    when(props.isValidationEnabled()).thenReturn(false);

    FactValidator uut =
        new FactValidator(props, mock(SchemaRegistry.class), mock(RegistryMetrics.class));
    Fact probeFact = Fact.builder().ns("foo").type("bar").version(1).buildWithoutPayload();
    assertThat(uut.validate(probeFact)).isEmpty();
  }

  @Test
  public void testFailsToValidateIfNotValidatable() {
    var registryMetrics = spy(new NOPRegistryMetrics());

    StoreConfigurationProperties props = mock(StoreConfigurationProperties.class);
    when(props.isSchemaRegistryConfigured()).thenReturn(true);
    when(props.isValidationEnabled()).thenReturn(true);
    when(props.isAllowUnvalidatedPublish()).thenReturn(false);

    FactValidator uut = new FactValidator(props, mock(SchemaRegistry.class), registryMetrics);
    Fact probeFact = Fact.builder().ns("foo").type("bar").buildWithoutPayload();
    assertThat(uut.validate(probeFact)).isNotEmpty();

    verify(registryMetrics).count(eq(EVENT.FACT_VALIDATION_FAILED), any(Tags.class));
  }

  @Test
  public void testValidateIfNotValidatableButAllowed() throws Exception {

    StoreConfigurationProperties props = mock(StoreConfigurationProperties.class);
    when(props.isSchemaRegistryConfigured()).thenReturn(true);
    when(props.isValidationEnabled()).thenReturn(true);
    when(props.isAllowUnvalidatedPublish()).thenReturn(true);

    FactValidator uut =
        new FactValidator(props, mock(SchemaRegistry.class), mock(RegistryMetrics.class));
    Fact probeFact = Fact.builder().ns("foo").type("bar").buildWithoutPayload();
    assertThat(uut.validate(probeFact)).isEmpty();
  }

  @Test
  public void testFailsToValidateIfValidatableButMissingSchema() throws Exception {
    final var registryMetrics = spy(new NOPRegistryMetrics());

    StoreConfigurationProperties props = mock(StoreConfigurationProperties.class);
    when(props.isSchemaRegistryConfigured()).thenReturn(true);
    when(props.isValidationEnabled()).thenReturn(true);
    when(props.isAllowUnvalidatedPublish()).thenReturn(false);
    SchemaRegistry sr = mock(SchemaRegistry.class);
    when(sr.get(Mockito.any(SchemaKey.class))).thenReturn(Optional.empty());

    FactValidator uut = new FactValidator(props, sr, registryMetrics);
    Fact probeFact = Fact.builder().ns("foo").type("bar").version(8).buildWithoutPayload();
    assertThat(uut.validate(probeFact)).isNotEmpty();

    verify(registryMetrics).count(eq(EVENT.SCHEMA_MISSING), any(Tags.class));
  }

  @Test
  public void testValidateWithMatchingSchema() throws Exception {

    StoreConfigurationProperties props = mock(StoreConfigurationProperties.class);
    when(props.isSchemaRegistryConfigured()).thenReturn(true);
    when(props.isValidationEnabled()).thenReturn(true);
    when(props.isAllowUnvalidatedPublish()).thenReturn(false);

    SchemaRegistry sr = mock(SchemaRegistry.class);
    String schemaJson =
        "\n"
            + "{\n"
            + "  \"additionalProperties\" : false,\n"
            + "  \"properties\" : {\n"
            + "    \"firstName\" : {\n"
            + "      \"type\": \"string\"\n"
            + "    }\n"
            + "  },\n"
            + "  \"required\": [\"firstName\"]\n"
            + "}";

    JsonSchema schema =
        ValidationConstants.fromJsonNode(ValidationConstants.JACKSON.readTree(schemaJson));
    when(sr.get(Mockito.any(SchemaKey.class))).thenReturn(Optional.of(schema));

    FactValidator uut = new FactValidator(props, sr, mock(RegistryMetrics.class));
    Fact probeFact =
        Fact.builder().ns("foo").type("bar").version(1).build("{\"firstName\":\"Peter\"}");
    assertThat(uut.validate(probeFact)).isEmpty();
  }

  @Test
  public void testValidateWithoutMatchingSchema() throws Exception {

    StoreConfigurationProperties props = mock(StoreConfigurationProperties.class);
    when(props.isSchemaRegistryConfigured()).thenReturn(true);
    when(props.isValidationEnabled()).thenReturn(true);
    when(props.isAllowUnvalidatedPublish()).thenReturn(true);

    SchemaRegistry sr = mock(SchemaRegistry.class);
    when(sr.get(Mockito.any(SchemaKey.class))).thenReturn(Optional.empty());

    FactValidator uut = new FactValidator(props, sr, mock(RegistryMetrics.class));
    Fact probeFact =
        Fact.builder().ns("foo").type("bar").version(1).build("{\"firstName\":\"Peter\"}");
    assertThat(uut.validate(probeFact)).isEmpty();
  }

  @Test
  public void testFailsToValidateWithMatchingSchemaButNonMatchingFact() throws Exception {
    final var registryMetrics = spy(new NOPRegistryMetrics());

    StoreConfigurationProperties props = mock(StoreConfigurationProperties.class);
    when(props.isSchemaRegistryConfigured()).thenReturn(true);
    when(props.isValidationEnabled()).thenReturn(true);
    when(props.isAllowUnvalidatedPublish()).thenReturn(false);

    SchemaRegistry sr = mock(SchemaRegistry.class);
    String schemaJson =
        "\n"
            + "{\n"
            + "  \"additionalProperties\" : false,\n"
            + "  \"properties\" : {\n"
            + "    \"firstName\" : {\n"
            + "      \"type\": \"string\"\n"
            + "    }\n"
            + "  },\n"
            + "  \"required\": [\"firstName\"]\n"
            + "}";

    JsonSchema schema =
        ValidationConstants.fromJsonNode(ValidationConstants.JACKSON.readTree(schemaJson));
    when(sr.get(Mockito.any(SchemaKey.class))).thenReturn(Optional.of(schema));

    FactValidator uut = new FactValidator(props, sr, registryMetrics);
    Fact probeFact = Fact.builder().ns("foo").type("bar").version(1).build("{}");
    assertThat(uut.validate(probeFact)).isNotEmpty();

    verify(registryMetrics).count(eq(EVENT.FACT_VALIDATION_FAILED), any(Tags.class));
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
