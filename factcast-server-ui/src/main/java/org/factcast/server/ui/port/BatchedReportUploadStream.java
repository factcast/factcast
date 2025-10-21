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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.NonNull;
import lombok.SneakyThrows;

public abstract class BatchedReportUploadStream {

  @NonNull private final JsonGenerator jsonGenerator;
  @NonNull private final OutputStream outputStream;

  @SneakyThrows
  protected BatchedReportUploadStream(
      @NonNull String reportName, @NonNull String queryString, @NonNull OutputStream outputStream) {
    this.outputStream = outputStream;
    final var jsonFactory = new JsonFactory();
    this.jsonGenerator = jsonFactory.createGenerator(outputStream);
    jsonGenerator.writeStartObject();
    jsonGenerator.writeStringField("name", reportName);
    jsonGenerator.writeStringField("generatedAt", OffsetDateTime.now().toString());
    jsonGenerator.writeStringField("query", queryString);
    // Open Array for events
    jsonGenerator.writeFieldName("events");
    jsonGenerator.writeStartArray();
  }

  public void writeBatch(List<ObjectNode> batch) {
    try {
      // passes on to outputStream
      for (var obj : batch) jsonGenerator.writeObject(obj);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write batch to S3 report upload", e);
    }
  }

  public void close() {
    try {
      jsonGenerator.writeEndArray();
      // TODO: maybe needs flush for file case?
      outputStream.close();
    } catch (Exception e) {
      throw new RuntimeException("Failed to close report upload", e);
    }
  }
}
