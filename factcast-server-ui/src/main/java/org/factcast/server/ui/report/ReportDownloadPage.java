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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import java.io.InputStream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.server.ui.port.ReportStore;
import org.factcast.server.ui.views.MainLayout;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.olli.FileDownloadWrapper;

@Route(value = "ui/reports", layout = MainLayout.class)
@PageTitle("Report Download")
@PermitAll
@SuppressWarnings({"java:S110", "java:S1948"})
@Slf4j
@NoCoverageReportToBeGenerated
public class ReportDownloadPage extends VerticalLayout {

  private final ReportStore reportStore;

  private final Grid<ReportEntry> reportGrid;
  private FileDownloadWrapper downloadWrapper;
  private String reportDownloadName = "report.json";

  public ReportDownloadPage(@NonNull ReportStore reportStore) {
    this.reportStore = reportStore;

    setWidthFull();
    setHeightFull();

    H2 heading = new H2("Your Reports");
    heading.getStyle().set("margin", "0 auto 0 0");

    Button refresh = new Button("Refresh");
    refresh.addClickListener(e -> fetchAvailableReports());
    final var header = new HorizontalLayout(heading, refresh);
    header.setAlignItems(Alignment.CENTER);

    Button download = new Button("Download");
    download.setEnabled(false);
    wrapDownloadButton(download);
    download.addClickListener(
        e -> {
          updateDownloadWrapper(reportDownloadName);
        });

    Button delete = new Button("Delete");
    delete.setEnabled(false);
    delete.getStyle().set("margin-inline-start", "auto");
    delete.addClickListener(
        e -> {
          final var userName = getLoggedInUserName();
          reportStore.delete(userName, reportDownloadName);
        });

    final var grid = new Grid<>(ReportEntry.class, false);
    grid.setSelectionMode(Grid.SelectionMode.SINGLE);
    grid.addColumn(ReportEntry::name).setHeader("Filename");
    grid.addColumn(ReportEntry::lastChanged).setHeader("Last Modified");
    // TODO: maybe add count of events
    grid.addSelectionListener(
        selection -> {
          log.info("Selected {}", selection.getFirstSelectedItem().get().name());
          reportDownloadName = selection.getFirstSelectedItem().get().name();
          download.setEnabled(true);
          delete.setEnabled(true);
        });
    this.reportGrid = grid;

    final var actions = new HorizontalLayout(downloadWrapper, delete);
    actions.getStyle().set("flex-wrap", "wrap");

    fetchAvailableReports();
    add(header, grid, actions);
  }

  private void fetchAvailableReports() {
    final var userReports = reportStore.listAllForUser(getLoggedInUserName());
    this.reportGrid.setItems(userReports);
    if (userReports.isEmpty()) {
      downloadWrapper.setEnabled(false);
    }
  }

  private void wrapDownloadButton(Button button) {
    this.downloadWrapper =
        new FileDownloadWrapper(new StreamResource("", InputStream::nullInputStream));
    this.downloadWrapper.wrapComponent(button);
  }

  private void updateDownloadWrapper(String fileName) {
    log.info("Preparing download for report {}", fileName);
    final var userName = getLoggedInUserName();
    final var reportDownloadStream = reportStore.get(userName, fileName);
    downloadWrapper.setResource(new StreamResource(fileName, () -> reportDownloadStream));
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
