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
import com.vaadin.flow.component.orderedlayout.ThemableLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.converter.StringToUuidConverter;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.NonNull;
import org.factcast.server.ui.id.BeanValidationUrlStateBinder;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.utils.Notifications;
import org.factcast.server.ui.views.JsonView;
import org.factcast.server.ui.views.MainLayout;

@Route(value = "ui/full", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Full")
@PermitAll
public class FullQueryPage extends VerticalLayout implements HasUrlParameter<String> {

  final FullQueryBean formBean = new FullQueryBean();

  private final ComboBox<String> ns;
  private final MultiSelectComboBox<String> type;

  private DatePicker since = new DatePicker("first serial of");
  private IntegerField limit = new IntegerField("limit");
  private IntegerField offset = new IntegerField("offset");
  private BigDecimalField from = new BigDecimalField("starting after serial");
  private TextField aggId = new AggregateIdField();
  private final JsonView jsonView = new JsonView();

  private FactRepository repo;

  private BeanValidationUrlStateBinder<FullQueryBean> b;

  public FullQueryPage(@NonNull FactRepository repo) {
    this.repo = repo;
    setWidthFull();
    setHeightFull();

    ns = new NameSpacesComboBox(repo.namespaces(Optional.empty()));
    type = new TypesMultiSelectComboBox();

    since.addValueChangeListener(
        e -> {
          updateFrom();
        });

    ns.addValueChangeListener(
        new ValueChangeListener<ValueChangeEvent<?>>() {

          @Override
          public void valueChanged(ValueChangeEvent<?> event) {
            updateTypeState();
            if (ns.isEmpty()) {
              type.setValue(new HashSet<String>());
            } else {
              type.setItems(repo.types(ns.getValue(), Optional.empty()));
            }
          }
        });

    updateFrom();

    b = createBinding();

    HorizontalLayout typeFilter = new HorizontalLayout(ns, type);
    typeFilter.setWidthFull();

    VerticalLayout extra =
        new VerticalLayout(
            new AggregateIdField(), new AccordionPanel("Meta", new MetaView(formBean)));

    noMarginAndPadding(extra);

    add(typeFilter, extra, new FromPanel(), new QuantityPanel(), new QueryButton());
    add(jsonView);

    updateTypeState();
  }

  private BeanValidationUrlStateBinder<FullQueryBean> createBinding() {
    var b = new BeanValidationUrlStateBinder<>(FullQueryBean.class);
    b.forField(ns).withNullRepresentation("").bind("ns");
    b.forField(type).withNullRepresentation(new HashSet<>()).bind("type");
    b.forField(from).withNullRepresentation(BigDecimal.ZERO).bind("from");
    b.forField(since).bind("since");
    b.forField(limit).bind("limit");
    b.forField(offset).bind("offset");
    b.forField(aggId)
        .withNullRepresentation("")
        .withConverter(new StringToUuidConverter("not a uuid"))
        .bind("aggId");
    b.readBean(formBean);
    return b;
  }

  private void updateTypeState() {
    type.setEnabled(!ns.isEmpty());
  }

  @Override
  public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
    final var location = event.getLocation();
    b.readFromQueryParams(location.getQueryParameters(), formBean);
    updateTypeState();
  }

  private void updateFrom() {
    LocalDate value = since.getValue();
    if (value == null) {
      from.setValue(null);
    } else {
      OptionalLong firstSerialFor = repo.lastSerialBefore(value);
      from.setValue(BigDecimal.valueOf(firstSerialFor.orElse(0)));
    }
  }

  void noMarginAndPadding(ThemableLayout l) {
    l.setMargin(false);
    l.setPadding(false);
  }

  class QuantityPanel extends HorizontalLayout {
    public QuantityPanel() {
      add(limit, offset);
    }
  }

  class FromPanel extends HorizontalLayout {
    public FromPanel() {
      VerticalLayout serialHelpers = new VerticalLayout();

      Button latestSerial = new Button("current (latest) serial");
      latestSerial.addClickListener(
          event -> {
            from.setValue(BigDecimal.valueOf(repo.latestSerial()));
          });
      latestSerial.setWidthFull();

      Button fromScratch = new Button("all");
      fromScratch.addClickListener(
          event -> {
            from.setValue(null);
          });

      fromScratch.setWidthFull();
      serialHelpers.add(since);
      serialHelpers.add(latestSerial);
      serialHelpers.add(fromScratch);
      VerticalLayout fromContainer = new VerticalLayout(from);
      add(fromContainer, serialHelpers);

      noMarginAndPadding(this);
      noMarginAndPadding(serialHelpers);
      noMarginAndPadding(fromContainer);
      from.setWidthFull();
    }
  }

  class QueryButton extends Button {
    public QueryButton() {
      super("query");

      addClickListener(
          event -> {
            try {
              b.writeBean(formBean);
              jsonView.renderFacts(repo.fetchChunk(formBean));
            } catch (ValidationException e) {
              Notifications.warn("Validation failed: " + e.getMessage());
            } catch (IllegalArgumentException e) {
              Notifications.warn(e.getMessage());
            }
          });
    }
  }

  class NameSpacesComboBox extends ComboBox<String> {
    public NameSpacesComboBox(Collection<String> items) {
      super("Namespaces");
      setItems(DataProvider.ofCollection(items));
      setRequired(true);
    }
  }

  class TypesMultiSelectComboBox extends MultiSelectComboBox<String> {
    public TypesMultiSelectComboBox() {
      super("Types", Collections.emptyList());
      setWidthFull();
    }
  }

  class AggregateIdField extends TextField {
    public AggregateIdField() {
      super("aggregate-id");
      setLabel("Aggregate-ID");
      setWidthFull();
    }
  }
}
