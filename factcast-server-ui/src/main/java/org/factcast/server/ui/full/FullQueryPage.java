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
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalLong;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.server.ui.plugins.JsonViewEntries;
import org.factcast.server.ui.plugins.JsonViewPluginService;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.utils.BeanValidationUrlStateBinder;
import org.factcast.server.ui.utils.Notifications;
import org.factcast.server.ui.views.FormContent;
import org.factcast.server.ui.views.JsonView;
import org.factcast.server.ui.views.MainLayout;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Route(value = "ui/full", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Query")
@PermitAll
@SuppressWarnings({"java:S110", "java:S1948"})
@Slf4j
@NoCoverageReportToBeGenerated
public class FullQueryPage extends VerticalLayout implements HasUrlParameter<String> {

  // externalizable state

  private final FullQueryBean formBean;

  // fields
  private final DatePicker since = new DatePicker("First Serial of Day");
  private final IntegerField limit = new IntegerField("Limit");
  private final IntegerField offset = new IntegerField("Offset");
  private final BigDecimalField from = new BigDecimalField("Starting Serial");
  private final Popup serialHelperOverlay = new Popup();
  private final JsonView jsonView = new JsonView();

  private final BeanValidationUrlStateBinder<FullQueryBean> binder;
  private final FactRepository repo;

  private final JsonViewPluginService jsonViewPluginService;
  private final FilterCriteriaViews factCriteriaViews = new FilterCriteriaViews();

  public FullQueryPage(
      @NonNull FactRepository repo, @NonNull JsonViewPluginService jsonViewPluginService) {
    setWidthFull();
    setHeightFull();

    this.repo = repo;
    this.jsonViewPluginService = jsonViewPluginService;

    formBean = new FullQueryBean(repo.latestSerial());

    serialHelperOverlay.setTarget(from.getElement());
    from.setAutocomplete(Autocomplete.OFF);
    since.addValueChangeListener(e -> updateFrom());

    binder = createBinding();
    factCriteriaViews.add(new FilterCriteriaView(repo, binder, formBean.getCriteria().get(0)));

    final var form = new FormContent(factCriteriaViews, new FromPanel(), formButtons());

    add(form);
    add(jsonView);
    add(serialHelperOverlay);

    updateFrom();
  }

  private BeanValidationUrlStateBinder<FullQueryBean> createBinding() {
    var b = new BeanValidationUrlStateBinder<>(FullQueryBean.class);
    b.forField(from).withNullRepresentation(BigDecimal.ZERO).bind("from");
    b.forField(since).bind("since");
    b.forField(limit).withNullRepresentation(FullQueryBean.DEFAULT_LIMIT).bind("limit");
    b.forField(offset).withNullRepresentation(0).bind("offset");

    b.readBean(formBean);
    return b;
  }

  @Override
  public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
    final var location = event.getLocation();
    // TODO handle list
    binder.readFromQueryParams(location.getQueryParameters(), formBean);
    factCriteriaViews.updateState();
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

  @NoCoverageReportToBeGenerated
  class FromPanel extends HorizontalLayout {
    public FromPanel() {
      setClassName("flex-wrap");
      setJustifyContentMode(JustifyContentMode.BETWEEN);

      Button latestSerial = new Button("Latest serial");
      latestSerial.addClickListener(
          event -> {
            from.setValue(BigDecimal.valueOf(repo.latestSerial()));
            serialHelperOverlay.hide();
          });

      Button fromScratch = new Button("From scratch");
      fromScratch.addClickListener(
          event -> {
            from.setValue(BigDecimal.ZERO);
            serialHelperOverlay.hide();
          });

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
    queryBtn.setDisableOnClick(true);
    queryBtn.addClickListener(
        event -> {
          try {
            binder.writeBean(formBean);
            log.info("{} runs query for {}", getLogggedInUserName(), formBean);
            List<Fact> dataFromStore = repo.fetchChunk(formBean);
            JsonViewEntries processedByPlugins = jsonViewPluginService.process(dataFromStore);
            jsonView.renderFacts(processedByPlugins);
          } catch (ValidationException e) {
            Notifications.warn(e.getMessage());
          } catch (Exception e) {
            Notifications.error(e.getMessage());
          } finally {
            queryBtn.setEnabled(true);
          }
        });

    final var resetBtn = new Button("Reset");
    resetBtn.addClickListener(
        event -> {
          formBean.reset();
          binder.readBean(formBean);
          factCriteriaViews.clear();
          factCriteriaViews.add(
              new FilterCriteriaView(repo, binder, formBean.getCriteria().get(0)));
        });

    final var hl = new HorizontalLayout(queryBtn, resetBtn);
    hl.setWidthFull();
    hl.addClassName("label-padding");

    return hl;
  }

  private String getLogggedInUserName() {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      return authentication.getName();
    } catch (Exception e) {
      log.warn("Cannot retrieve logged in user");
      return "UNKNOWN";
    }
  }
}
