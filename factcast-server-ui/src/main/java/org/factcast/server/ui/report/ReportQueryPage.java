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
package org.factcast.server.ui.report;

import com.vaadin.componentfactory.Popup;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.value.ValueChangeMode;
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
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.port.ReportStore;
import org.factcast.server.ui.utils.BeanValidationUrlStateBinder;
import org.factcast.server.ui.utils.Notifications;
import org.factcast.server.ui.views.FormContent;
import org.factcast.server.ui.views.MainLayout;
import org.factcast.server.ui.views.filter.FilterBean;
import org.factcast.server.ui.views.filter.FilterCriteriaViews;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Route(value = "ui/report", layout = MainLayout.class)
@PageTitle("Report")
@PermitAll
@SuppressWarnings({"java:S110", "java:S1948"})
@Slf4j
@NoCoverageReportToBeGenerated
public class ReportQueryPage extends VerticalLayout implements HasUrlParameter<String> {

  // externalizable state
  private final ReportFilterBean formBean;

  private final ReportStore reportStore;

  // fields
  private final DatePicker since = new DatePicker("First Serial of Day");
  private final BigDecimalField from = new BigDecimalField("Starting Serial");
  private final TextField fileNameField = new TextField("File Name");
  private final Popup serialHelperOverlay = new Popup();

  private final BeanValidationUrlStateBinder<FilterBean> binder;
  private final FactRepository repo;

  private final FilterCriteriaViews factCriteriaViews;
  private final Button queryBtn = new Button("Generate");
  private String fileName = "events.json";

  public ReportQueryPage(@NonNull FactRepository repo, @NonNull ReportStore reportStore) {
    setWidthFull();
    setHeightFull();

    this.repo = repo;
    this.reportStore = reportStore;

    formBean = new ReportFilterBean(repo.latestSerial());

    serialHelperOverlay.setTarget(from.getElement());
    from.setId("starting-serial");
    from.setAutocomplete(Autocomplete.OFF);
    since.addValueChangeListener(e -> updateFrom());

    binder = createBinding();

    factCriteriaViews = new FilterCriteriaViews(repo, binder, formBean);

    final var accordion = new Accordion();
    accordion.setWidthFull();
    accordion.add("Conditions", factCriteriaViews);

    final var form = new FormContent(accordion, new FromPanel(), queryButtons());

    add(form);
    add(serialHelperOverlay);

    updateFrom();
  }

  private BeanValidationUrlStateBinder<FilterBean> createBinding() {
    var b = new BeanValidationUrlStateBinder<>(FilterBean.class);
    b.forField(from).withNullRepresentation(BigDecimal.ZERO).bind("from");
    b.forField(since).bind("since");

    b.readBean(formBean);
    return b;
  }

  @Override
  public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
    final var location = event.getLocation();

    binder.readFromQueryParams(location.getQueryParameters(), formBean);
    factCriteriaViews.rebuild();
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
      overlayContent.setAlignItems(Alignment.STRETCH);
      serialHelperOverlay.add(overlayContent);

      from.setWidth("auto");

      fileNameField.setLabel("Report File Name");
      fileNameField.setPlaceholder("events");
      fileNameField.setTooltipText("The name of the file generated by the report.");
      fileNameField.setClearButtonVisible(true);
      fileNameField.setSuffixComponent(new Span(".json"));
      fileNameField.setValueChangeMode(ValueChangeMode.ON_BLUR);
      fileNameField.addValueChangeListener(
          e -> {
            queryBtn.setEnabled(true);
            // TODO sanitize
            fileName = e.getValue() + ".json";
            log.info("File name set to {}", fileName);
          });

      add(from, fileNameField);
    }
  }

  private void runQuery() {
    try {
      queryBtn.setEnabled(false);
      binder.writeBean(formBean);
      final var loggedInUserName = getLoggedInUserName();
      log.info("{} runs query for {}", loggedInUserName, formBean);

      List<Fact> dataFromStore = repo.fetchChunk(formBean);
      log.info("Found {} entries", dataFromStore.size());
      if (!dataFromStore.isEmpty()) {
        reportStore.save(
            loggedInUserName, new Report(fileName, dataFromStore, formBean.toString()));
      } else {
        // TODO add notification for user
        log.info("No report was saved as no data was found");
      }
    } catch (ValidationException e) {
      Notifications.warn(e.getMessage());
    } catch (Exception e) {
      Notifications.error(e.getMessage());
    }
    // Not re-enabling the queryReportBtn to not incentivise users to generate it
    // multiple times.
  }

  @NonNull
  private HorizontalLayout queryButtons() {
    queryBtn.addClickShortcut(Key.ENTER);
    queryBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    queryBtn.setDisableOnClick(true);
    queryBtn.addClickListener(event -> runQuery());

    final var resetBtn = new Button("Reset");
    resetBtn.addClickListener(
        event -> {
          formBean.reset();
          binder.readBean(formBean);
          factCriteriaViews.rebuild();
          queryBtn.setEnabled(true);
        });

    final var hl = new HorizontalLayout(queryBtn, resetBtn);
    hl.setWidthFull();
    hl.addClassName("label-padding");

    return hl;
  }

  private String getLoggedInUserName() {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      return authentication.getName();
    } catch (Exception e) {
      log.warn("Cannot retrieve logged in user");
      return "UNKNOWN";
    }
  }
}
