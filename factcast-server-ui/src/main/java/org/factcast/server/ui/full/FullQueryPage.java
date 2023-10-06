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

import com.vaadin.flow.component.HasValue.ValueChangeEvent;
import com.vaadin.flow.component.HasValue.ValueChangeListener;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.OptionalLong;
import org.factcast.server.ui.id.BeanValidationUrlStateBinder;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.views.MainLayout;

@Route(value = "ui/full", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Full")
@AnonymousAllowed
public class FullQueryPage extends VerticalLayout implements HasUrlParameter<String> {

  final FullQueryBean formBean = new FullQueryBean();

  private final ComboBox<String> ns = new ComboBox<>("Namespaces");
  private final MultiSelectComboBox<String> type = new MultiSelectComboBox<>("Types");

  private DatePicker since = new DatePicker("first Serial of");
  private IntegerField limit = new IntegerField("limit");
  private IntegerField offset = new IntegerField("offset");
  private BigDecimalField from = new BigDecimalField("starting from Serial");

  private FactRepository repo;

  private BeanValidationUrlStateBinder<FullQueryBean> b;

  public FullQueryPage(FactRepository repo) {

    this.repo = repo;
    // setResponsiveSteps(new FormLayout.ResponsiveStep("150em", 2));
    setWidthFull();
    type.setWidthFull();
    from.setWidthFull();
    type.setEnabled(false);

    // ns.setLabel("Namespace");
    ns.setItems(DataProvider.ofCollection(repo.namespaces(Optional.empty())));
    ns.setRequired(true);

    ns.addValueChangeListener(
        new ValueChangeListener<ValueChangeEvent<?>>() {

          @Override
          public void valueChanged(ValueChangeEvent<?> event) {
            type.setEnabled(!ns.isEmpty());
            if (ns.isEmpty()) {
              type.setValue(new HashSet<String>());
            } else {
              type.setItems(repo.types(ns.getValue(), Optional.empty()));
            }
          }
        });

    since.addValueChangeListener(
        e -> {
          updateFrom();
        });

    updateFrom();
    // ns.getGrid().addColumn(s -> s).setHeader("Namespace name");

    type.setItems(DataProvider.ofCollection(new LinkedList<String>()));
    // type.getGrid().addColumn(s -> s).setHeader("Event type name");
    // type.setLabel("Type");

    b = new BeanValidationUrlStateBinder<>(FullQueryBean.class);
    b.forField(ns).withNullRepresentation("").bind("ns");
    b.forField(type).withNullRepresentation(new HashSet<>()).bind("type");
    b.forField(from).withNullRepresentation(BigDecimal.ZERO).bind("from");
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

    HorizontalLayout quantity = new HorizontalLayout(limit, offset);

    VerticalLayout serialHelpers = new VerticalLayout();

    Button latestSerial = new Button("current (latest) serial");
    latestSerial.addClickListener(
        event -> {
          from.setValue(BigDecimal.valueOf(repo.lastSerial()));
        });
    serialHelpers.add(since);
    serialHelpers.add(latestSerial);

    HorizontalLayout fromLayout = new HorizontalLayout(new VerticalLayout(from), serialHelpers);

    add(typeFilter, extra, fromLayout, quantity, query);
  }

  @Override
  public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
    final var location = event.getLocation();

    b.readFromQueryParams(location.getQueryParameters(), formBean);
  }

  private void updateFrom() {
    LocalDate value = since.getValue();
    if (value == null) {
      from.setValue(null);
    } else {
      OptionalLong firstSerialFor = repo.firstSerialFor(value);
      from.setValue(BigDecimal.valueOf(firstSerialFor.orElse(0)));
    }
  }
}
