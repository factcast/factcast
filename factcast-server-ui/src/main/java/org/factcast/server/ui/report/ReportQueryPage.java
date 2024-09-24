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
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.provider.DataProvider;
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
import org.factcast.server.ui.plugins.JsonViewPluginService;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.port.ReportStore;
import org.factcast.server.ui.utils.BeanValidationUrlStateBinder;
import org.factcast.server.ui.utils.Notifications;
import org.factcast.server.ui.views.FormContent;
import org.factcast.server.ui.views.MainLayout;
import org.factcast.server.ui.views.filter.FilterBean;
import org.factcast.server.ui.views.filter.FilterCriteriaViews;
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
  private final JsonViewPluginService jsonViewPluginService;
  private final BeanValidationUrlStateBinder<FilterBean> binder;
  private final FactRepository repo;
  private final DataProvider<ReportEntry, Void> reportProvider;

  // fields
  private final DatePicker since = new DatePicker("First Serial of Day");
  private final BigDecimalField from = new BigDecimalField("Starting Serial");
  private final TextField fileNameField = new TextField("File Name");
  private final Popup serialHelperOverlay = new Popup();
  private final Button queryBtn = new Button("Generate");
  private String fileName = "events.json";
  private String reportDownloadName;

  private final FilterCriteriaViews factCriteriaViews;
  private final ReportDownloadSection downloadSection;

  private final String userName = SecurityContextHolder.getContext().getAuthentication().getName();

  public ReportQueryPage(
      @NonNull FactRepository repo,
      @NonNull ReportStore reportStore,
      @NonNull JsonViewPluginService jsonViewPluginService) {
    setWidthFull();
    setHeightFull();

    this.repo = repo;
    this.reportStore = reportStore;
    this.jsonViewPluginService = jsonViewPluginService;

    formBean = new ReportFilterBean(repo.latestSerial());
    binder = createUrlStateBinding();
    factCriteriaViews = new FilterCriteriaViews(repo, binder, formBean);
    reportProvider = getReportProvider();

    final var accordion = new Accordion();
    accordion.setWidthFull();
    accordion.add("Conditions", factCriteriaViews);

    final var queryFormSection = new FormContent(accordion, new InputFields(), queryButtons());
    add(queryFormSection, serialHelperOverlay);

    final var reportViewHeader = getReportHeaderSection(reportProvider);
    downloadSection = new ReportDownloadSection(reportStore, reportProvider);
    final var reportGrid = getReportGrid();
    add(reportViewHeader, reportGrid, downloadSection);

    updateFrom();
  }

  private Grid<ReportEntry> getReportGrid() {
    final var grid = new Grid<>(ReportEntry.class, false);
    grid.setSelectionMode(Grid.SelectionMode.SINGLE);
    grid.addColumn(ReportEntry::name).setHeader("Filename");
    grid.addColumn(ReportEntry::lastChanged).setHeader("Last Modified");
    grid.addSelectionListener(
        selection -> {
          if (!selection.getAllSelectedItems().isEmpty()) {
            log.info("Selected {}", selection.getFirstSelectedItem().get().name());
            this.reportDownloadName = selection.getFirstSelectedItem().get().name();
            downloadSection.refreshForFile(reportDownloadName);
          }
        });
    grid.setDataProvider(reportProvider);
    grid.setMinHeight("300px");

    return grid;
  }

  private static HorizontalLayout getReportHeaderSection(
      DataProvider<ReportEntry, Void> reportProvider) {
    H3 heading = new H3("Your Reports");
    heading.getStyle().set("margin", "0 auto 0 0");

    Button refresh = new Button("Refresh");
    refresh.addClickListener(e -> reportProvider.refreshAll());
    final var header = new HorizontalLayout(heading, refresh);
    header.setAlignItems(Alignment.CENTER);
    return header;
  }

  private BeanValidationUrlStateBinder<FilterBean> createUrlStateBinding() {
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
  class InputFields extends HorizontalLayout {
    public InputFields() {
      setClassName("flex-wrap");
      setJustifyContentMode(JustifyContentMode.BETWEEN);

      serialHelperOverlay.setTarget(from.getElement());
      from.setId("starting-serial");
      from.setAutocomplete(Autocomplete.OFF);
      since.addValueChangeListener(e -> updateFrom());

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
      fileNameField.setMinLength(1);
      fileNameField.setTooltipText("The name of the file generated by the report.");
      fileNameField.setClearButtonVisible(true);
      fileNameField.setSuffixComponent(new Span(".json"));
      fileNameField.setValueChangeMode(ValueChangeMode.EAGER);
      fileNameField.addValueChangeListener(
          e -> {
            if (!fileNameField.isEmpty()) {
              queryBtn.setEnabled(true);
              fileName = sanitizeFileName(e.getValue()) + ".json";
            } else {
              queryBtn.setEnabled(false);
            }
          });

      add(from, fileNameField);
    }
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

  private void runQuery() {
    try {
      queryBtn.setEnabled(false);
      binder.writeBean(formBean);
      log.info("{} runs query for {}", userName, formBean);

      // TODO: ensure this won't fail if the query takes a long time.
      List<Fact> dataFromStore = repo.fetchChunk(formBean);
      log.info("Found {} entries", dataFromStore.size());
      if (!dataFromStore.isEmpty()) {

        final var processedFacts = jsonViewPluginService.process(dataFromStore);

        try {
          reportStore.save(userName, new Report(fileName, processedFacts, formBean.toString()));
          reportProvider.refreshAll();
        } catch (IllegalArgumentException e) {
          displayWarning(e.getMessage());
        }
      } else {
        displayWarning(
            "No data was found for this query and therefore report creation is skipped.");
      }
    } catch (ValidationException e) {
      Notifications.warn(e.getMessage());
    } catch (Exception e) {
      Notifications.error(e.getMessage());
    }
    // Not re-enabling the queryReportBtn to not incentivise users to generate it
    // multiple times.
  }

  private DataProvider<ReportEntry, Void> getReportProvider() {
    return DataProvider.fromCallbacks(
        // First callback fetches items based on a query
        query -> {
          final var userReports = reportStore.listAllForUser(this.userName);
          return userReports.stream().skip(query.getOffset()).limit(query.getLimit());
        },
        // Second callback fetches the number of items for a query
        query -> {
          final var userReports = reportStore.listAllForUser(this.userName);
          return userReports.size();
        });
  }

  private static void displayWarning(String message) {
    Notification notification = Notification.show(message);
    notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
  }

  private String sanitizeFileName(String fileName) {
    return fileName.replaceAll("[^a-zA-Z0-9-_]", "");
  }
}
