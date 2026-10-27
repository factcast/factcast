/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.server.ui.wicket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.factcast.server.ui.views.filter.AbstractFilterBean;

final class QueryState {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final jakarta.validation.Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private QueryState() {}

  static void restore(PageParameters parameters, Object bean) {
    var value = parameters.get("state");
    if (!value.isNull()) {
      try {
        MAPPER.readerForUpdating(bean).readValue(value.toString());
        if (bean instanceof AbstractFilterBean filter) {
          if (filter.getCriteria() == null
              || filter.getCriteria().isEmpty()
              || filter.getCriteria().stream()
                  .anyMatch(
                      criteria ->
                          criteria == null
                              || criteria.getMeta() == null
                              || criteria.getMeta().contains(null))) {
            throw new IllegalArgumentException("Invalid conditions");
          }
        }
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "The query link contains invalid state. Reset the form to start again.", e);
      }
    }
  }

  static String json(Object bean) {
    try {
      return MAPPER.writeValueAsString(bean);
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot save query state", e);
    }
  }

  static String script(String state) {
    return "FactCastUI.saveState("
        + json(state)
            .replace("<", "\\u003c")
            .replace(">", "\\u003e")
            .replace("&", "\\u0026")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
        + ");";
  }

  static void validate(Object bean) {
    var violations = VALIDATOR.validate(bean);
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException(
          violations.stream()
              .map(v -> v.getPropertyPath() + " " + v.getMessage())
              .sorted()
              .collect(java.util.stream.Collectors.joining("; ")));
    }
    if (bean instanceof AbstractFilterBean filter) {
      if (filter.getCriteria() == null || filter.getCriteria().isEmpty())
        throw new IllegalArgumentException("Add at least one condition.");
      for (var criteria : filter.getCriteria()) {
        if (criteria == null || criteria.getNs() == null || criteria.getNs().isBlank())
          throw new IllegalArgumentException("Select a namespace for every condition.");
        if (criteria.getType() != null && criteria.getType().isEmpty()) criteria.setType(null);
        if (criteria.getMeta() == null)
          throw new IllegalArgumentException("Invalid metadata filters.");
        for (var meta : criteria.getMeta()) {
          if (meta.getKey() == null || meta.getKey().isBlank() || meta.getValue() == null)
            throw new IllegalArgumentException("Supply a metadata key and value.");
        }
      }
      for (BigDecimal serial : new BigDecimal[] {filter.getFrom(), filter.getTo()}) {
        if (serial != null) {
          try {
            serial.longValueExact();
          } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                "Serials must be whole numbers within the long integer range.");
          }
        }
      }
      if (filter.getFrom() != null
          && filter.getTo() != null
          && filter.getFrom().compareTo(filter.getTo()) > 0)
        throw new IllegalArgumentException("End serial must not precede starting serial.");
    }
  }
}
