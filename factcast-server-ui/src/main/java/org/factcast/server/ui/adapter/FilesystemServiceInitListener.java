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

import com.vaadin.flow.server.*;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Provides an endpoint for downloading local files. Files must be stored in a directory named after
 * the user within the provided persistence directory path.
 */
@RequiredArgsConstructor
@Slf4j
public class FilesystemServiceInitListener implements VaadinServiceInitListener {
  final String persistenceDir;

  @Override
  public void serviceInit(ServiceInitEvent event) {
    log.info("Registering file download handler for {}", persistenceDir);
    event.addRequestHandler(
        (VaadinSession session, VaadinRequest request, VaadinResponse response) -> {
          if (request.getPathInfo().startsWith("/files/")) {
            final String requestedFile = getRequestedFilename(request);
            final String userName = getUserName();
            final var filePath = Path.of(persistenceDir, userName, requestedFile);

            if (Files.exists(filePath)) {
              response.setContentType("application/json");
              response.setHeader(
                  "Content-Disposition", "attachment; filename=\"" + filePath.getFileName() + "\"");

              try (FileInputStream fileInputStream = new FileInputStream(filePath.toFile());
                  OutputStream outputStream = response.getOutputStream()) {
                fileInputStream.transferTo(outputStream);
              }
              return true; // Indicate that the request has been handled
            } else {
              response.sendError(404, "Report not found");
            }
          }
          return false; // Pass to the next handler
        });
  }

  static String getUserName() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  static String getRequestedFilename(VaadinRequest request) {
    return request.getPathInfo().substring("/files/".length());
  }
}
