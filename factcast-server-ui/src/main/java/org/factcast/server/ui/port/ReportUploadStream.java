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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.TokenStreamFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.server.ui.report.ReportFilterBean;

@Slf4j
public abstract class ReportUploadStream {
  @NonNull private final JsonGenerator jsonGenerator;

  @SneakyThrows
  protected ReportUploadStream(
      @NonNull TokenStreamFactory jsonFactory,
      @NonNull String reportName,
      @NonNull ReportFilterBean query,
      @NonNull OutputStream outputStream) {
    log.debug("Initializing report upload stream for report '{}'", reportName);
    this.jsonGenerator = jsonFactory.createGenerator(outputStream);
    jsonGenerator.writeStartObject();
    jsonGenerator.writeStringField("name", reportName);
    jsonGenerator.writeStringField("generatedAt", OffsetDateTime.now().toString());
    jsonGenerator.writeFieldName("query");
    jsonGenerator.writePOJO(query);
    // Open Array for events
    jsonGenerator.writeFieldName("events");
    jsonGenerator.writeStartArray();
  }

  @SneakyThrows
  public void writeToBatch(ObjectNode obj) {
    // passes on to outputStream
    jsonGenerator.writePOJO(obj);
  }

  @SneakyThrows
  public void close() {
    log.debug("Attempting to close upload stream");
    jsonGenerator.writeEndArray();
    jsonGenerator.writeEndObject();
    jsonGenerator.flush();
    jsonGenerator.close();
    log.debug("Report upload stream closed successfully");
  }
}
