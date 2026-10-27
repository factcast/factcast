/*
 * Copyright © 2017-2025 factcast.org
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

import com.fasterxml.jackson.core.TokenStreamFactory;
import java.io.FileOutputStream;
import java.nio.file.Path;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.server.ui.report.ReportFilterBean;

public class FileReportUploadStream extends ReportUploadStream {

  public FileReportUploadStream(
      @NonNull TokenStreamFactory jsonFactory,
      @NonNull Path filePath,
      @NonNull String reportName,
      @NonNull ReportFilterBean query) {
    super(jsonFactory, reportName, query, getFileOutputStream(filePath));
  }

  @SneakyThrows
  private static FileOutputStream getFileOutputStream(@NonNull Path path) {
    return new FileOutputStream(path.toFile());
  }
}
