package org.factcast.store.registry.http;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SpecificationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ValidationConstantsTest {

  @InjectMocks private ValidationConstants underTest;

  @Nested
  class WhenJsoningString2SchemaV7 {
    private final String SCHEMA_JSON = "SCHEMA_JSON";

    @BeforeEach
    void setup() {}

    @Test
    void createsWorkingSchema() {
      Schema schema = ValidationConstants.jsonString2SchemaV7("{}");
      assertThat(schema).isNotNull();
      // does not throw:
      schema.validate("{}");
    }

    @Test
    void isV7() {
      assertThat(ValidationConstants.getLoaderBuilder())
          .hasFieldOrPropertyWithValue("specVersion", SpecificationVersion.DRAFT_7);
    }
  }
}
