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

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import io.micrometer.core.instrument.Tags;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.store.registry.http.ValidationConstants;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.validation.schema.SchemaKey;

@RequiredArgsConstructor
public class FactValidator {

  private static final List<FactValidationError> VALIDATION_OK = Collections.emptyList();

  private final StoreConfigurationProperties props;

  private final SchemaRegistry registry;

  private final RegistryMetrics registryMetrics;

  public List<FactValidationError> validate(Fact fact) {
    if (props.isSchemaRegistryConfigured() && props.isValidationEnabled()) {
      if (isValidateable(fact)) {
        return doValidate(fact);
      } else {
        if (!props.isAllowUnvalidatedPublish()) {
          registryMetrics.count(
              RegistryMetrics.EVENT.FACT_VALIDATION_FAILED,
              Tags.of(RegistryMetrics.TAG_IDENTITY_KEY, SchemaKey.from(fact).toString()));

          return Lists.newArrayList(
              new FactValidationError(
                  "Fact is not validatable. (usually lacks necessary information like namespace,"
                      + " type or version)"));
        }
      }
    }

    return VALIDATION_OK;
  }

  private List<FactValidationError> doValidate(Fact fact) {
    SchemaKey key = SchemaKey.from(fact);
    Optional<JsonSchema> optSchema = registry.get(key);
    if (optSchema.isPresent()) {

      JsonSchema jsonSchema = optSchema.get();
      ProcessingReport report;
      try {
        JsonNode toValidate = ValidationConstants.JACKSON.readTree(fact.jsonPayload());
        report = jsonSchema.validate(toValidate);
        if (report.isSuccess()) {
          return VALIDATION_OK;
        } else {
          List<FactValidationError> ret = new LinkedList<>();

          report.forEach(
              m -> {
                ret.add(new FactValidationError(m.getLogLevel().toString(), m.getMessage()));
              });

          registryMetrics.count(
              RegistryMetrics.EVENT.FACT_VALIDATION_FAILED,
              Tags.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()));

          return ret;
        } // validateJson(Node) ends
      } catch (IOException | ProcessingException e) {
        return Lists.newArrayList(
            new FactValidationError("Fact is not parseable. " + e.getMessage()));
      }
    } else {
      if (!props.isAllowUnvalidatedPublish()) {
        registryMetrics.count(
            RegistryMetrics.EVENT.SCHEMA_MISSING,
            Tags.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()));

        return Lists.newArrayList(
            new FactValidationError(
                "Fact is not validatable. The schema for " + key + " is missing."));
      } else {
        return VALIDATION_OK;
      }
    }
  }

  @VisibleForTesting
  protected static boolean isValidateable(Fact fact) {
    return fact.ns() != null && fact.type() != null && fact.version() > 0;
  }
}
