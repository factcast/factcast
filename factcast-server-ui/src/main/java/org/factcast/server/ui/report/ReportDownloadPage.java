/*
 * Copyright © 2017-2024 factcast.org
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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.server.ui.port.ReportStore;
import org.factcast.server.ui.views.MainLayout;
import org.springframework.security.core.context.SecurityContextHolder;

@Route(value = "ui/reports", layout = MainLayout.class)
@PageTitle("Report Download")
@PermitAll
@SuppressWarnings({"java:S110", "java:S1948"})
@Slf4j
@NoCoverageReportToBeGenerated
public class ReportDownloadPage extends VerticalLayout {

  private final ReportStore reportStore;

  private final Grid<ReportEntry> reportGrid;
  private final ReportDownloadArea downloadArea;
  private final DataProvider<ReportEntry, Void> reportProvider;
  private String reportDownloadName = "report.json";
  private final String userName = SecurityContextHolder.getContext().getAuthentication().getName();

  public ReportDownloadPage(@NonNull ReportStore reportStore) {
    this.reportStore = reportStore;

    setWidthFull();
    setHeightFull();

    this.reportProvider = getReportProvider();

    H2 heading = new H2("Your Reports");
    heading.getStyle().set("margin", "0 auto 0 0");

    Button refresh = new Button("Refresh");
    refresh.addClickListener(e -> reportProvider.refreshAll());
    final var header = new HorizontalLayout(heading, refresh);
    header.setAlignItems(Alignment.CENTER);

    this.downloadArea = new ReportDownloadArea(reportStore, reportProvider);

    final var grid = new Grid<>(ReportEntry.class, false);
    grid.setSelectionMode(Grid.SelectionMode.SINGLE);
    grid.addColumn(ReportEntry::name).setHeader("Filename");
    grid.addColumn(ReportEntry::lastChanged).setHeader("Last Modified");
    grid.addSelectionListener(
        selection -> {
          if (!selection.getAllSelectedItems().isEmpty()) {
            log.info("Selected {}", selection.getFirstSelectedItem().get().name());
            this.reportDownloadName = selection.getFirstSelectedItem().get().name();
            downloadArea.refreshForFile(reportDownloadName);
          }
        });
    grid.setDataProvider(reportProvider);
    this.reportGrid = grid;

    //    fetchAvailableReports();
    add(header, grid, downloadArea);
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
}
