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

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;
import lombok.NonNull;

public class FileBatchedReportUploadStream extends BatchedReportUploadStream {

  public FileBatchedReportUploadStream(
      @NonNull Path filePath, @NonNull String reportName, @NonNull String queryString) {
    super(reportName, queryString, getFileOutputStream(filePath));
  }

  private static FileOutputStream getFileOutputStream(@NonNull Path path) {
    try {
      return new FileOutputStream(path.toFile());
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to create FileOutputStream for report upload to file: " + path.toAbsolutePath(),
          e);
    }
  }

  @Override
  public void writeBatch(List<ObjectNode> batch) {}

  @Override
  public void close() {}
}
