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

import java.util.*;
import java.util.stream.*;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.factcast.core.Fact;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import io.micrometer.core.instrument.Tags;

import lombok.RequiredArgsConstructor;

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

  @VisibleForTesting
  List<FactValidationError> doValidate(Fact fact) {
    SchemaKey key = SchemaKey.from(fact);
    Optional<Schema> optSchema = registry.get(key);
    if (optSchema.isPresent()) {
      Schema jsonSchema = optSchema.get();
      try {
        JSONObject toValidate = new JSONObject(fact.jsonPayload());
        return tryValidate(key, jsonSchema, toValidate);
      } catch (JSONException e) {
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
  List<FactValidationError> tryValidate(SchemaKey key, Schema jsonSchema, JSONObject toValidate) {
    try {
      jsonSchema.validate(toValidate);
      return VALIDATION_OK;
    } catch (ValidationException e) {
      List<String> errors = e.getAllMessages();
      registryMetrics.count(
          RegistryMetrics.EVENT.FACT_VALIDATION_FAILED,
          Tags.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()));
      return errors.stream().map(FactValidationError::new).collect(Collectors.toList());
    }
  }

  @VisibleForTesting
  protected static boolean isValidateable(Fact fact) {
    return fact.ns() != null && fact.type() != null && fact.version() > 0;
  }
}
