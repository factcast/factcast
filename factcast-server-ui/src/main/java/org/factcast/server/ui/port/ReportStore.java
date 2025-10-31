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
package org.factcast.server.ui.port;

import java.net.URL;
import java.util.List;
import lombok.NonNull;
import org.factcast.server.ui.report.ReportEntry;
import org.factcast.server.ui.report.ReportFilterBean;

public interface ReportStore {

  ReportUploadStream createBatchUpload(
      @NonNull String userName, @NonNull String reportName, @NonNull ReportFilterBean query);

  URL getReportDownload(@NonNull String userName, @NonNull String reportName);

  List<ReportEntry> listAllForUser(@NonNull String userName);

  void delete(@NonNull String userName, @NonNull String reportName);
}
