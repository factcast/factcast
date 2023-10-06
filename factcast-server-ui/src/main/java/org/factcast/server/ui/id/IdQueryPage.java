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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.converter.StringToIntegerConverter;
import com.vaadin.flow.data.converter.StringToUuidConverter;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.views.JsonView;
import org.factcast.server.ui.views.MainLayout;

@Route(value = "ui/id", layout = MainLayout.class)
@PageTitle("by Id")
@AnonymousAllowed
public class IdQueryPage extends VerticalLayout implements HasUrlParameter<String> {
  private final IdQueryBean formBean = new IdQueryBean();
  private final BeanValidationUrlStateBinder<IdQueryBean> b =
      new BeanValidationUrlStateBinder<>(IdQueryBean.class);

  public IdQueryPage(FactRepository fc) {
    setWidthFull();
    setHeightFull();

    final var idInput = idInput();
    final var versionInput = versionInput();
    final var inputFields = new HorizontalLayout(idInput, versionInput);
    inputFields.setWidthFull();

    final var jsonView = new JsonView();
    final var queryButton = queryButton(fc, jsonView);

    add(inputFields, queryButton, jsonView);

    b.readBean(formBean);
  }

  @Override
  public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
    final var location = event.getLocation();

    b.readFromQueryParams(location.getQueryParameters(), formBean);
  }

  @NonNull
  private Button queryButton(FactRepository fc, JsonView jsonView) {
    final var query = new Button("query");

    query.addClickListener(
        event -> {
          try {
            b.writeBean(formBean);

            if (formBean.getId() != null) {
              var fact = fc.findBy(formBean);
              System.out.println("fact by id: " + fact.map(Fact::jsonPayload).orElse("not found"));

              fact.ifPresent(jsonView::renderFact);
            }

          } catch (ValidationException e) {
            throw new RuntimeException(e);
          }
        });
    return query;
  }

  @NonNull
  private TextField versionInput() {
    final var version = new TextField("version");
    b.forField(version)
        .withNullRepresentation("as published")
        .withConverter(new StringToIntegerConverter("not a valid version"))
        .bind("version");
    return version;
  }

  @NonNull
  private TextField idInput() {
    final var id = new TextField("id");
    id.setLabel("Fact-id");
    id.setWidthFull();

    b.forField(id)
        .withNullRepresentation("")
        .withConverter(new StringToUuidConverter("not a uuid"))
        .bind("id");
    return id;
  }
}
