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

import java.util.List;
import lombok.NonNull;
import org.factcast.server.ui.report.Report;
import org.factcast.server.ui.report.ReportEntry;

public interface ReportStore {

  /** Saves a report for a given user if it does not exist yet. */
  void save(@NonNull String userName, @NonNull Report report);

  //  InputStream getReportByteStream(@NonNull String userName, @NonNull String reportName);

  byte[] getReportAsBytes(@NonNull String userName, @NonNull String reportName);

  List<ReportEntry> listAllForUser(@NonNull String userName);

  void delete(@NonNull String userName, @NonNull String reportName);
}
