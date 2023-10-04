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
package org.factcast.server.ui.full;

import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.provider.DataProvider;
import java.util.HashSet;
import java.util.Optional;
import org.factcast.server.ui.port.FactRepository;

public class FullQueryView extends VerticalLayout {

  final FullQueryBean formBean = new FullQueryBean();

  private final MultiSelectComboBox<String> ns = new MultiSelectComboBox<>("Namespaces");
  private final MultiSelectComboBox<String> type = new MultiSelectComboBox<>("Types");

  private DatePicker since = new DatePicker("since");
  private IntegerField limit = new IntegerField("limit");
  private IntegerField offset = new IntegerField("offset");

  FullQueryView(FactRepository repo) {

    // setResponsiveSteps(new FormLayout.ResponsiveStep("150em", 2));
    setWidthFull();
    type.setWidthFull();

    // ns.setLabel("Namespace");
    ns.setItems(DataProvider.ofCollection(repo.namespaces(Optional.empty())));
    // ns.getGrid().addColumn(s -> s).setHeader("Namespace name");

    type.setItems(DataProvider.ofCollection(repo.types(Optional.empty())));
    //		type.getGrid().addColumn(s -> s).setHeader("Event type name");
    //		type.setLabel("Type");

    Binder<FullQueryBean> b = new BeanValidationBinder<>(FullQueryBean.class);
    b.forField(ns).withNullRepresentation(new HashSet<>()).bind("ns");
    b.forField(type).withNullRepresentation(new HashSet<>()).bind("type");
    b.forField(since).bind("since");
    b.forField(limit).bind("limit");
    b.forField(offset).bind("offset");
    // TODO check grid binding
    b.readBean(formBean);

    Button query = new Button("query");
    query.addClickListener(
        event -> {
          try {
            b.writeBean(formBean);
          } catch (ValidationException e) {
            throw new RuntimeException(e);
          }
          System.out.println("query " + formBean);
        });

    HorizontalLayout typeFilter = new HorizontalLayout(ns, type);
    typeFilter.setWidthFull();

    VerticalLayout extra =
        new VerticalLayout(
            new AccordionPanel("Aggregate-Ids", new AggregateIdView(formBean)),
            new AccordionPanel("Meta", new MetaView(formBean)));
    extra.setWidthFull();

    HorizontalLayout quantity = new HorizontalLayout(since, limit, offset);
    add(typeFilter, quantity, extra, query);
  }
}
