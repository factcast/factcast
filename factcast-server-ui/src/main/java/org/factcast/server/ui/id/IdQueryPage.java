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

import static com.google.common.collect.Lists.newArrayList;
import static org.factcast.server.ui.id.SelectedVersionConverter.AS_PUBLISHED;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.converter.StringToUuidConverter;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.util.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.server.ui.plugins.JsonViewPluginService;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.utils.BeanValidationUrlStateBinder;
import org.factcast.server.ui.utils.Notifications;
import org.factcast.server.ui.views.FormContent;
import org.factcast.server.ui.views.JsonView;
import org.factcast.server.ui.views.MainLayout;

@Slf4j
@Route(value = "ui/id", layout = MainLayout.class)
@PageTitle("Query by Fact-ID")
@PermitAll
@SuppressWarnings("java:S1948")
@NoCoverageReportToBeGenerated
public class IdQueryPage extends VerticalLayout implements HasUrlParameter<String> {
  private final IdQueryBean formBean = new IdQueryBean();
  private final BeanValidationUrlStateBinder<IdQueryBean> b =
      new BeanValidationUrlStateBinder<>(IdQueryBean.class);

  private Fact cachedFact;
  private String selectedVersion = AS_PUBLISHED;

  public IdQueryPage(FactRepository fc, JsonViewPluginService jsonViewPluginService) {
    setHeightFull();
    setWidthFull();

    final var idInput = idInput();
    final var versionSelector = getVersionSelector();

    setupVersionsUpdateOnInputChange(fc, idInput, versionSelector);

    final var inputFields = new HorizontalLayout(idInput, versionSelector);
    inputFields.setWidthFull();

    final var jsonView = new JsonView();
    final var buttons = formButtons(fc, jsonViewPluginService, jsonView);

    final var form = new FormContent(inputFields, buttons);

    add(form, jsonView);

    b.readBean(formBean);
  }

  private void setupVersionsUpdateOnInputChange(
      FactRepository fc, TextField idInput, Select<String> versionSelector) {
    idInput.addValueChangeListener(
        event -> {
          boolean versionSelectionEnabled = false;
          this.selectedVersion = AS_PUBLISHED;

          if (containsValidUUID(idInput)) {
            try {
              // Fetch fact to get ns and type
              b.writeBean(formBean);
              final var fact = fc.findBy(formBean);
              if (fact.isPresent()) {
                // Save fact to prevent subsequent queries with the same version
                this.cachedFact = fact.get();
                final var availableVersions = newArrayList(AS_PUBLISHED);
                availableVersions.addAll(
                    fc.versions(cachedFact.ns(), cachedFact.type()).stream()
                        .map(String::valueOf)
                        .sorted(Comparator.reverseOrder())
                        .toList());
                versionSelector.setItems(availableVersions);
                versionSelectionEnabled = true;
              }
            } catch (Exception e) {
              reset(versionSelector, false);
            }
          }
          reset(versionSelector, versionSelectionEnabled);
        });
  }

  @Override
  public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
    final var location = event.getLocation();

    b.readFromQueryParams(location.getQueryParameters(), formBean);
  }

  @NonNull
  private HorizontalLayout formButtons(
      FactRepository fc, JsonViewPluginService jsonViewPluginService, JsonView jsonView) {
    final var queryBtn = new Button("Query");
    queryBtn.addClickShortcut(Key.ENTER);
    queryBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    queryBtn.setDisableOnClick(true);
    queryBtn.addClickListener(event -> executeQuery(fc, jsonViewPluginService, jsonView, queryBtn));

    final var resetBtn = new Button("Reset");
    resetBtn.addClickListener(
        event -> {
          b.readBean(null);
          jsonView.clear();
        });

    final var hl = new HorizontalLayout(queryBtn, resetBtn);
    hl.setWidthFull();
    hl.addClassName("label-padding");

    return hl;
  }

  private void executeQuery(
      FactRepository fc,
      JsonViewPluginService jsonViewPluginService,
      JsonView jsonView,
      Button queryBtn) {
    try {
      b.writeBean(formBean);

      if (formBean.getId() != null) {
        // Only query again if a specific version was selected or there somehow is no fact saved
        // from the version query
        var fact =
            selectedVersion.equals(AS_PUBLISHED) && cachedFact != null
                ? Optional.of(cachedFact)
                : fc.findBy(formBean);

        fact.ifPresentOrElse(
            f -> jsonView.renderFact(jsonViewPluginService.process(f), 0),
            () -> Notifications.warn("Fact not found"));
      }

    } catch (ValidationException e) {
      Notifications.warn(e.getMessage());
    } catch (TransformationException e) {
      Notifications.error(e.getMessage());
    } finally {
      queryBtn.setEnabled(true);
    }
  }

  @NonNull
  private Select<String> getVersionSelector() {
    final var versionSelector = new Select<String>();
    versionSelector.setLabel("Version");
    versionSelector.setId("version-selector");
    versionSelector.setEnabled(false);

    versionSelector.addValueChangeListener(event -> this.selectedVersion = event.getValue());

    b.forField(versionSelector)
        .withNullRepresentation(AS_PUBLISHED)
        .withConverter(new SelectedVersionConverter())
        .bind("version");

    return versionSelector;
  }

  /** Sets selected value to AS_PUBLISHED and en/disables the selector as specified. */
  private static void reset(@NonNull Select<String> versionSelector, boolean enabled) {
    versionSelector.setValue(AS_PUBLISHED);
    versionSelector.setEnabled(enabled);
  }

  private boolean containsValidUUID(@NonNull TextField inputField) {
    if (!inputField.isEmpty() && !inputField.getValue().isBlank()) {
      try {
        UUID.fromString(inputField.getValue());
        return true;
      } catch (IllegalArgumentException e) {
        Notifications.warn("Invalid UUID: " + e.getMessage());
        return false;
      }
    }
    return false;
  }

  @NonNull
  private TextField idInput() {
    final var id = new TextField("id");
    id.setLabel("Fact-ID");
    id.setAutofocus(true);
    id.setWidthFull();
    id.setValueChangeMode(ValueChangeMode.EAGER);

    b.forField(id)
        .withNullRepresentation("")
        .asRequired()
        .withConverter(new StringToUuidConverter("not a uuid"))
        .bind("id");
    return id;
  }
}
