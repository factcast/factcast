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
package org.factcast.server.ui.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vaadin.flow.data.provider.DataProvider;
import org.factcast.server.ui.port.ReportStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.*;

@ExtendWith(MockitoExtension.class)
class ReportDownloadSectionTest {
  private static final String USERNAME = "username";
  private static final String FILENAME = "filename";
  @Mock ReportStore reportStore;

  @Mock DataProvider<ReportEntry, Void> dataProvider;

  @Mock SecurityContext ctx;

  @Mock Authentication authentication;

  @Test
  void defaultButtonSettings() {
    // ARRANGE
    when(ctx.getAuthentication()).thenReturn(authentication);
    when(authentication.getName()).thenReturn("foo");

    try (var securityContextHolder = Mockito.mockStatic(SecurityContextHolder.class)) {
      securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(ctx);
      // ACT
      ReportDownloadSection uut = new ReportDownloadSection(reportStore, dataProvider);
      // ASSERT
      assertThat(uut.downloadBtn().isEnabled()).isFalse();
      assertThat(uut.downloadBtn().isDisableOnClick()).isTrue();
      assertThat(uut.deleteBtn().isEnabled()).isFalse();
    }
  }

  @Test
  void buttonSettingsAfterRefresh() {
    // ARRANGE
    when(ctx.getAuthentication()).thenReturn(authentication);
    when(authentication.getName()).thenReturn(USERNAME);

    try (var securityContextHolder = Mockito.mockStatic(SecurityContextHolder.class)) {
      securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(ctx);
      ReportDownloadSection uut = new ReportDownloadSection(reportStore, dataProvider);
      // ACT
      uut.refreshForFile(FILENAME);
      // ASSERT
      assertThat(uut.downloadBtn().isEnabled()).isTrue();
      assertThat(uut.deleteBtn().isEnabled()).isTrue();
      assertThat(uut.fileName()).isEqualTo(FILENAME);
    }
  }
}
