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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.util.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.server.ui.plugins.JsonViewPluginService;
import org.factcast.server.ui.port.*;
import org.factcast.server.ui.utils.*;
import org.factcast.server.ui.views.*;
import org.factcast.server.ui.views.filter.FilterCriteriaViews;
import org.springframework.security.core.context.SecurityContextHolder;

@Route(value = "ui/report", layout = MainLayout.class)
@PageTitle("Report")
@PermitAll
@SuppressWarnings({"java:S110", "java:S1948"})
@Slf4j
@NoCoverageReportToBeGenerated
public class ReportQueryPage extends VerticalLayout implements HasUrlParameter<String> {

  // externalizeable state
  private final ReportFilterBean formBean;

  private final ReportStore reportStore;
  private final JsonViewPluginService jsonViewPluginService;
  private final BeanValidationUrlStateBinder<ReportFilterBean> binder;
  private final FactRepository repo;
  private final DataProvider<ReportEntry, Void> reportProvider;

  // fields
  private final FileNameInputField fileNameField = new FileNameInputField("Report File Name");
  private final Button queryBtn = new Button("Generate");
  private String reportDownloadName;

  private final FilterCriteriaViews<ReportFilterBean> factCriteriaViews;
  private final ReportDownloadSection downloadSection;

  private final String userName = SecurityContextHolder.getContext().getAuthentication().getName();

  // Nice to have: load query when selecting report
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
    final var inputPanel = new SerialInputPanel(repo);

    binder = createUrlStateBinding(inputPanel);
    factCriteriaViews = new FilterCriteriaViews<>(repo, binder, formBean);
    reportProvider = getReportProvider();

    fileNameField.disableButtonWhenBlank(queryBtn);

    final var accordion = new Accordion();
    accordion.setWidthFull();
    accordion.add("Conditions", factCriteriaViews);

    final var queryFormSection =
        new FormContent(accordion, fileNameField, inputPanel, queryButtons());
    add(queryFormSection);
    add(inputPanel.fromSerialHelperOverlay());
    add(inputPanel.toSerialHelperOverlay());

    final var reportViewHeader = getReportHeaderSection(reportProvider);
    downloadSection = new ReportDownloadSection(reportStore, reportProvider);
    final var reportGrid = getReportGrid();
    add(reportViewHeader, reportGrid, downloadSection);

    inputPanel.updateFrom();
    inputPanel.updateTo();
  }

  private Grid<ReportEntry> getReportGrid() {
    final var grid = new Grid<>(ReportEntry.class, false);
    grid.setId("report-table");
    grid.setSelectionMode(Grid.SelectionMode.SINGLE);
    grid.addColumn(ReportEntry::name).setHeader("Filename");
    grid.addColumn(ReportEntry::lastChanged).setHeader("Created");
    grid.addSelectionListener(
        selection -> {
          if (!selection.getAllSelectedItems().isEmpty()) {
            this.reportDownloadName = selection.getFirstSelectedItem().orElseThrow().name();
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

  private BeanValidationUrlStateBinder<ReportFilterBean> createUrlStateBinding(
      SerialInputPanel inputFields) {
    var b = new BeanValidationUrlStateBinder<>(ReportFilterBean.class);
    b.forField(inputFields.from()).withNullRepresentation(BigDecimal.ZERO).bind("from");
    b.forField(inputFields.to()).bind("to");
    b.forField(inputFields.since()).bind("since");
    b.forField(inputFields.until()).bind("until");

    b.readBean(formBean);
    return b;
  }

  @Override
  public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
    final var location = event.getLocation();

    binder.readFromQueryParams(location.getQueryParameters(), formBean);
    factCriteriaViews.rebuild();
  }

  @Getter
  @NoCoverageReportToBeGenerated
  private static class FileNameInputField extends TextField {
    private String value = "events.json";

    public FileNameInputField(@NonNull String label) {
      super(label);
      this.setPlaceholder("events");
      this.setMinLength(1);
      this.setTooltipText("The name of the file generated by the report.");
      this.setClearButtonVisible(true);
      this.setSuffixComponent(new Span(".json"));
      this.setValueChangeMode(ValueChangeMode.EAGER);
    }

    public void disableButtonWhenBlank(@NonNull Button button) {
      this.addValueChangeListener(
          e -> {
            if (!this.isEmpty()) {
              button.setEnabled(true);
              this.value = sanitizeFileName(e.getValue()) + ".json";
            } else {
              button.setEnabled(false);
            }
          });
    }

    private String sanitizeFileName(String fileName) {
      return fileName.replaceAll("[^a-zA-Z0-9-_]", "");
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

      final var reportUploadStream =
          reportStore.createBatchUpload(userName, fileNameField.value(), formBean);
      final long numberOfProcessedFacts =
          repo.fetchAndProcessAll(
              formBean,
              fact -> {
                reportUploadStream.writeToBatch(processFact(fact));
              });
      // Finish report json and close stream
      reportUploadStream.close();

      log.info("Found {} entries", numberOfProcessedFacts);
      if (numberOfProcessedFacts == 0) {
        displayWarning(
            "No data was found for this query and therefore report creation is skipped.");
        // no report was produced, report with same name could still be produced,
        // people will probably want to adjust their filter criteria and try again right away
        queryBtn.setEnabled(true);
      } else {
        reportProvider.refreshAll();
      }

    } catch (ValidationException e) {
      Notifications.warn(e.getMessage());
    } catch (IllegalArgumentException e) {
      displayWarning(e.getMessage());
    } catch (Exception e) {
      Notifications.error(e.getMessage());
    }
    // Not re-enabling the queryReportBtn to not incentivise users to generate it
    // multiple times.
  }

  private ObjectNode processFact(Fact fact) {
    return jsonViewPluginService.process(fact).fact();
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
}
