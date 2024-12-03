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

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.provider.DataProvider;
import lombok.extern.slf4j.Slf4j;
import org.factcast.server.ui.port.ReportStore;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public class ReportDownloadSection extends HorizontalLayout {

  private final ReportStore reportStore;
  private final DataProvider<ReportEntry, Void> dataProvider;

  private final Button downloadBtn =
      new Button("Download"); // new LazyDownloadButton("Download", this::getFileName,
  // this::requestCurrentFile);
  private final Button deleteBtn = new Button("Delete");
  private final String userName = SecurityContextHolder.getContext().getAuthentication().getName();
  private String fileName;

  public ReportDownloadSection(
      ReportStore reportStore, DataProvider<ReportEntry, Void> dataProvider) {
    this.reportStore = reportStore;
    this.dataProvider = dataProvider;

    this.downloadBtn.setEnabled(false);
    this.downloadBtn.setDisableOnClick(true);
    this.downloadBtn.addClickListener(this::downloadClickListener);
    //    this.downloadBtn.addDownloadStartsListener(this::onDownloadStarted);

    this.deleteBtn.setEnabled(false);
    this.deleteBtn.addClickListener(this::deletionClickListener);

    add(downloadBtn, deleteBtn);
    this.getStyle().set("flex-wrap", "wrap");
  }

  private String getFileName() {
    return fileName;
  }

  public void refreshForFile(String fileName) {
    log.debug("Refreshing Download Area");
    this.fileName = fileName;
    downloadBtn.setEnabled(true);
    deleteBtn.setEnabled(true);
  }

  private ReportDownload requestCurrentFile() {
    return reportStore.getReport(this.userName, this.fileName);
  }

  /** Opens the download link in a new tab */
  private void downloadClickListener(ClickEvent<Button> buttonClickEvent) {
    Button button = buttonClickEvent.getSource();
    button.setText("Preparing download...");
    getUI().orElseGet(UI::new).getPage().open(requestCurrentFile().url().toString());
    button.setEnabled(false);
    button.setText("Download");
  }

  private void deletionClickListener(ClickEvent<Button> buttonClickEvent) {
    Button button = buttonClickEvent.getSource();
    log.info("Deleting file {}", this.fileName);
    button.setEnabled(false);
    reportStore.delete(this.userName, this.fileName);
    downloadBtn.setEnabled(false);

    // in order to remove the entry from the grid
    dataProvider.refreshAll();
  }
}
