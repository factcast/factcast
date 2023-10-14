/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.ui.id;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.QueryParameters;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.UnaryOperator;
import lombok.SneakyThrows;
import org.springframework.web.util.UriComponentsBuilder;

public class BeanValidationUrlStateBinder<T> extends BeanValidationBinder<T> {
  private final ObjectMapper om = createOm();

  public BeanValidationUrlStateBinder(Class<T> beanType) {
    super(beanType);
  }

  public BeanValidationUrlStateBinder(Class<T> beanType, boolean scanNestedDefinitions) {
    super(beanType, scanNestedDefinitions);
  }

  private ObjectMapper createOm() {
    var om = new ObjectMapper();
    om.registerModules(new JavaTimeModule());
    return om;
  }

  @Override
  public void writeBean(T t) throws ValidationException {
    super.writeBean(t);

    updateClientUrl(
        x ->
            UriComponentsBuilder.fromUri(x)
                .replaceQueryParam("state", writeValueAsString(t))
                .build()
                .toUri());
  }

  public void readFromQueryParams(QueryParameters queryParameters, T bean) {
    final var parametersMap = queryParameters.getParameters();

    if (parametersMap.containsKey("state")) {
      try {
        om.readerForUpdating(bean).readValue(parametersMap.get("state").get(0));
        readBean(bean);
      } catch (JsonProcessingException e) {
        // do nothing
      }
    }
  }

  @SneakyThrows
  private String writeValueAsString(Object value) {
    return om.writeValueAsString(value);
  }

  @Override
  public void readBean(T t) {
    super.readBean(t);

    if (t == null) {
      updateClientUrl(
          x ->
              UriComponentsBuilder.fromUri(x)
                  .replaceQueryParam("state", List.of())
                  .build()
                  .toUri());
    }
  }

  private static void updateClientUrl(UnaryOperator<URI> urlReplacer) {
    UI.getCurrent()
        .getPage()
        .fetchCurrentURL(
            x -> {
              try {
                UI.getCurrent()
                    .getPage()
                    .getHistory()
                    .replaceState(null, urlReplacer.apply(x.toURI()).toString());
              } catch (URISyntaxException e) {
                // do nothing
              }
            });
  }
}
