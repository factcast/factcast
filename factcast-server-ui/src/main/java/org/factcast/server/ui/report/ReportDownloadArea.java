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
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.server.StreamResource;
import java.io.ByteArrayInputStream;
import lombok.extern.slf4j.Slf4j;
import org.factcast.server.ui.port.ReportStore;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.olli.FileDownloadWrapper;

@Slf4j
public class ReportDownloadArea extends HorizontalLayout {

  private final ReportStore reportStore;
  private final DataProvider<ReportEntry, Void> dataProvider;

  private final Button downloadBtn = new Button("Download");
  private Button deleteBtn = new Button("Delete");
  private final String userName = SecurityContextHolder.getContext().getAuthentication().getName();

  public ReportDownloadArea(ReportStore reportStore, DataProvider<ReportEntry, Void> dataProvider) {
    this.reportStore = reportStore;
    this.dataProvider = dataProvider;
    this.downloadBtn.setEnabled(false);
    this.deleteBtn.setEnabled(false);
    add(downloadBtn, deleteBtn);
    this.getStyle().set("flex-wrap", "wrap");
  }

  public void refreshForFile(String fileName) {
    log.debug("Refreshing Download Area");
    removeAll();

    final var downloadWrapper = createDownloadWrapper(fileName, downloadBtn);
    downloadBtn.setEnabled(true);

    // Required to remove previous click listener
    deleteBtn = new Button("Delete");
    configureDeletionListener(fileName);
    deleteBtn.setEnabled(true);

    add(downloadWrapper, deleteBtn);
  }

  public void configureDeletionListener(String fileName) {
    deleteBtn.addClickListener(
        e -> {
          log.info("Deleting file {}", fileName);
          reportStore.delete(this.userName, fileName);
          deleteBtn.setEnabled(false);
          downloadBtn.setEnabled(false);

          // in order to remove the entry from the grid
          dataProvider.refreshAll();
        });
  }

  private FileDownloadWrapper createDownloadWrapper(String fileName, Button downloadButton) {
    removeAll();
    StreamResource streamResource =
        new StreamResource(
            fileName,
            () -> {
              var r = reportStore.getReportAsBytes(this.userName, fileName);
              return new ByteArrayInputStream(r);
            });
    final var wrapper = new FileDownloadWrapper(streamResource);
    wrapper.wrapComponent(downloadButton);

    return wrapper;
  }
}
