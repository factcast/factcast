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
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.converter.StringToIntegerConverter;
import com.vaadin.flow.data.converter.StringToUuidConverter;
import org.factcast.core.Fact;
import org.factcast.server.ui.port.FactRepository;

public class IdQueryView extends FlexLayout {
  private final IdQueryBean formBean;

  TextField id = new TextField("id");
  TextField version = new TextField("version");

  public IdQueryView(FactRepository fc) {

    this.formBean = new IdQueryBean();
    id.setWidthFull();

    Binder<IdQueryBean> b = new BeanValidationBinder<>(IdQueryBean.class);
    b.forField(id)
        .withNullRepresentation("")
        .withConverter(new StringToUuidConverter("not a uuid"))
        .bind("id");
    b.forField(version)
        .withNullRepresentation("as published")
        .withConverter(new StringToIntegerConverter("not a valid version"))
        .bind("version");
    b.readBean(formBean);

    Button query = new Button("query");
    query.addClickListener(
        event -> {
          try {
            b.writeBean(formBean);

            if (formBean.getId() != null) {
              var fact = fc.findBy(formBean);
              System.out.println("fact by id: " + fact.map(Fact::jsonPayload).orElse("not found"));
            }

          } catch (ValidationException e) {
            throw new RuntimeException(e);
          }
        });

    id.setLabel("Fact-id");

    HorizontalLayout hl = new HorizontalLayout(id, version);
    hl.setWidthFull();

    VerticalLayout vl = new VerticalLayout(hl, query);
    vl.setWidthFull();
    add(vl);
    setWidthFull();
  }
}
