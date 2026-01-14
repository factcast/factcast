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
package org.factcast.server.ui.adapter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.vaadin.flow.server.*;
import java.io.*;
import java.nio.file.Files;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.*;

@ExtendWith(MockitoExtension.class)
class FilesystemServiceInitListenerTest {

  @Mock ServiceInitEvent event;
  @Mock VaadinSession session;
  @Mock VaadinRequest request;
  @Mock VaadinResponse response;
  @Mock SecurityContext ctx;
  @Mock Authentication authentication;

  static final String DIR = "/tmp";
  static final String USERNAME = "username";
  static final String FILENAME = "filename";
  static final String VALID_PREFIX = "/files/";
  static final String INVALID_PREFIX = "/NOTfiles/";
  static final String CONTENT = "some content";

  final FilesystemServiceInitListener uut = new FilesystemServiceInitListener(DIR);

  @Test
  @SneakyThrows
  void happyCase() {
    // ARRANGE
    final var validRequestedFilename = VALID_PREFIX + FILENAME;
    final var out = new ByteArrayOutputStream();

    when(request.getPathInfo()).thenReturn(validRequestedFilename);
    when(ctx.getAuthentication()).thenReturn(authentication);
    when(authentication.getName()).thenReturn(USERNAME);
    when(response.getOutputStream()).thenReturn(out);

    String filenameWithContent = String.join(File.separator, List.of(DIR, USERNAME, FILENAME));
    writeToNewFile(filenameWithContent, CONTENT);

    try (MockedStatic<SecurityContextHolder> securityContext =
            mockStatic(SecurityContextHolder.class);
        MockedStatic<Files> files = mockStatic(Files.class)) {
      securityContext.when(SecurityContextHolder::getContext).thenReturn(ctx);
      files.when(() -> Files.exists(any())).thenReturn(true);
      {
        // ACT
        uut.serviceInit(event);

        // ASSERT
        ArgumentCaptor<RequestHandler> captor = ArgumentCaptor.forClass(RequestHandler.class);
        verify(event).addRequestHandler(captor.capture());
        boolean result = captor.getValue().handleRequest(session, request, response);

        assertThat(result).isTrue();
        verify(response).setContentType("application/json");
        verify(response)
            .setHeader("Content-Disposition", "attachment; filename=\"" + FILENAME + "\"");
        assertThat(out.toString()).hasToString(CONTENT);
      }
    }
  }

  @Test
  @SneakyThrows
  void requestedFileHasWrongSuffix() {
    // ARRANGE
    final var invalidRequestedFilename = INVALID_PREFIX + FILENAME;
    when(request.getPathInfo()).thenReturn(invalidRequestedFilename);

    // ACT
    uut.serviceInit(event);

    // ASSERT
    ArgumentCaptor<RequestHandler> captor = ArgumentCaptor.forClass(RequestHandler.class);
    verify(event).addRequestHandler(captor.capture());
    boolean result = captor.getValue().handleRequest(session, request, response);
    assertThat(result).isFalse();
  }

  @Test
  @SneakyThrows
  void requestedFileDoesNotExist() {
    // ARRANGE
    final var requestedFilename = VALID_PREFIX + FILENAME;

    when(request.getPathInfo()).thenReturn(requestedFilename);
    when(ctx.getAuthentication()).thenReturn(authentication);
    when(authentication.getName()).thenReturn(USERNAME);

    try (MockedStatic<SecurityContextHolder> securityContext =
            mockStatic(SecurityContextHolder.class);
        MockedStatic<Files> files = mockStatic(Files.class)) {
      securityContext.when(SecurityContextHolder::getContext).thenReturn(ctx);
      files.when(() -> Files.exists(any())).thenReturn(false);
      {
        // ACT
        uut.serviceInit(event);

        // ASSERT
        ArgumentCaptor<RequestHandler> captor = ArgumentCaptor.forClass(RequestHandler.class);
        verify(event).addRequestHandler(captor.capture());
        boolean result = captor.getValue().handleRequest(session, request, response);

        assertThat(result).isFalse();
        verify(response).sendError(404, "Report not found");
      }
    }
  }

  private static void writeToNewFile(String filenameWithContent, String content)
      throws IOException {
    File fileWithContent = new File(filenameWithContent);
    fileWithContent.delete();
    fileWithContent.getParentFile().mkdirs();
    fileWithContent.createNewFile();

    PrintWriter writer = new PrintWriter(filenameWithContent, UTF_8);
    writer.print(content);
    writer.close();
  }
}
