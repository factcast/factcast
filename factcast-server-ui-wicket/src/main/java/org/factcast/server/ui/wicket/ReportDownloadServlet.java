/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.server.ui.wicket;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.file.*;
import org.factcast.server.security.auth.FactCastUser;
import org.factcast.server.ui.adapter.FileSystemReportStore;
import org.springframework.security.core.context.SecurityContextHolder;

/** Downloads are always resolved relative to the authenticated user's directory. */
class ReportDownloadServlet extends HttpServlet {
  private final FileSystemReportStore store;

  ReportDownloadServlet(FileSystemReportStore store) {
    this.store = store;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof FactCastUser)) {
      response.sendError(401);
      return;
    }
    var name = request.getPathInfo();
    if (name == null || !name.matches("/[a-zA-Z0-9_-]+\\.json")) {
      response.sendError(404);
      return;
    }
    Path root = Path.of(store.persistenceDir).toAbsolutePath().normalize();
    Path directory = root.resolve(authentication.getName()).normalize();
    Path file = directory.resolve(name.substring(1)).normalize();
    if (!directory.startsWith(root)
        || !file.startsWith(directory)
        || !Files.isRegularFile(file)
        || !file.toRealPath().startsWith(root.toRealPath())
        || !directory.toRealPath().startsWith(root.toRealPath())
        || !file.toRealPath().startsWith(directory.toRealPath())) {
      response.sendError(404);
      return;
    }
    response.setContentType("application/json");
    response.setHeader(
        "Content-Disposition", "attachment; filename=\"" + file.getFileName() + "\"");
    response.setHeader("Cache-Control", "no-store");
    Files.copy(file, response.getOutputStream());
  }
}
