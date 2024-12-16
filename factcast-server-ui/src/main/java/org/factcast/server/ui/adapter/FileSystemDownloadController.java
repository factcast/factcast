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
package org.factcast.server.ui.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.server.ui.port.ReportStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class FileSystemDownloadController {

  // tODO: this whole controller should only be started if fieSystemReportStore is present
  private final ReportStore fileSystemReportStore;

  @GetMapping(value = "/hello")
  public String hello() {
    log.info("XXX call on GET hello XXX");
    return "Hello, world!";
  }

  @GetMapping(value = "/download")
  public ResponseEntity<Resource> download(@RequestParam String id) {
    log.info("Download request for id: {}", id);
    // TODO: check the file is for the user
    try {
      final var resourceStream = fileSystemReportStore.getReportAsStream("test", id);
      final var contentLength = resourceStream.contentLength();
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_JSON)
          .contentLength(contentLength)
          .body(resourceStream);
    } catch (Exception e) {
      log.error("Error providing file download from filesystem", e);
      return ResponseEntity.internalServerError().build();
    }
  }
}
