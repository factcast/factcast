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

import com.vaadin.componentfactory.Popup;
import com.vaadin.flow.component.HasValue.ValueChangeEvent;
import com.vaadin.flow.component.HasValue.ValueChangeListener;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.Autocomplete;
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
import org.factcast.server.ui.views.DefaultContent;
import org.factcast.server.ui.views.JsonView;
import org.factcast.server.ui.views.MainLayout;

@Route(value = "ui/full", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Query")
@PermitAll
public class FullQueryPage extends DefaultContent implements HasUrlParameter<String> {

  final FullQueryBean formBean = new FullQueryBean();

  private final ComboBox<String> ns;
  private final MultiSelectComboBox<String> type;
  private final MetaButton metaButton;

  private DatePicker since = new DatePicker("First Serial of Day");
  private IntegerField limit = new IntegerField("Limit");
  private IntegerField offset = new IntegerField("Offset");
  private BigDecimalField from = new BigDecimalField("Starting Serial");
  private TextField aggId = new AggregateIdField();
  private Popup serialHelperOverlay = new Popup();
  private final JsonView jsonView = new JsonView();

  private FactRepository repo;

  private BeanValidationUrlStateBinder<FullQueryBean> b;

  public FullQueryPage(@NonNull FactRepository repo) {
    this.repo = repo;

    ns = new NameSpacesComboBox(repo.namespaces(Optional.empty()));
    type = new TypesMultiSelectComboBox();

    serialHelperOverlay.setTarget(from.getElement());
    from.setAutocomplete(Autocomplete.OFF);

    since.addValueChangeListener(e -> updateFrom());

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

    b = createBinding();

    final var nsAndTypeFilter = new HorizontalLayout(ns, type);
    nsAndTypeFilter.setWidthFull();

    metaButton = new MetaButton(formBean);
    final var aggIdAndMeta = new HorizontalLayout(aggId, metaButton);
    aggIdAndMeta.setWidthFull();
    aggIdAndMeta.setAlignItems(Alignment.BASELINE);

    final var form =
        new VerticalLayout(nsAndTypeFilter, aggIdAndMeta, new FromPanel(), formButtons());
    form.setSpacing(false);
    form.setPadding(false);

    add(form);
    add(jsonView);
    add(serialHelperOverlay);

    updateTypeState();
    updateFrom();
  }

  private BeanValidationUrlStateBinder<FullQueryBean> createBinding() {
    var b = new BeanValidationUrlStateBinder<>(FullQueryBean.class);
    b.forField(ns).withNullRepresentation("").asRequired().bind("ns");
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
    metaButton.update();
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

  class FromPanel extends HorizontalLayout {
    public FromPanel() {
      setClassName("flex-wrap");
      setJustifyContentMode(JustifyContentMode.BETWEEN);

      Button latestSerial = new Button("Latest serial");
      latestSerial.addClickListener(
          event -> from.setValue(BigDecimal.valueOf(repo.latestSerial())));

      Button fromScratch = new Button("From scratch");
      fromScratch.addClickListener(event -> from.setValue(BigDecimal.ZERO));

      final var heading = new H4("Select Starting Serial");
      final var overlayContent = new VerticalLayout(heading, since, latestSerial, fromScratch);
      overlayContent.setSpacing(false);
      overlayContent.getThemeList().add("spacing-xs");
      overlayContent.setAlignItems(FlexComponent.Alignment.STRETCH);
      serialHelperOverlay.add(overlayContent);

      from.setWidth("auto");
      limit.setWidth("auto");
      offset.setWidth("auto");
      add(from, limit, offset);
    }
  }

  @NonNull
  private HorizontalLayout formButtons() {
    final var queryBtn = new Button("Query");
    queryBtn.addClickShortcut(Key.ENTER);
    queryBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    queryBtn.addClickListener(
        event -> {
          try {
            b.writeBean(formBean);
            jsonView.renderFacts(repo.fetchChunk(formBean));
          } catch (ValidationException e) {
            Notifications.warn(e.getMessage());
          } catch (Exception e) {
            Notifications.error(e.getMessage());
          }
        });

    final var resetBtn = new Button("Reset");
    resetBtn.addClickListener(
        event -> {
          b.readBean(null);
          formBean.getMeta().clear();
          metaButton.update();
        });

    final var hl = new HorizontalLayout(queryBtn, resetBtn);
    hl.setWidthFull();
    hl.addClassName("label-padding");

    return hl;
  }

  class NameSpacesComboBox extends ComboBox<String> {
    public NameSpacesComboBox(Collection<String> items) {
      super("Namespace");
      setItems(DataProvider.ofCollection(items));
      setAutoOpen(true);
      setAutofocus(true);
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
      setPlaceholder("UUID");
      setWidth("100%");
    }
  }
}
