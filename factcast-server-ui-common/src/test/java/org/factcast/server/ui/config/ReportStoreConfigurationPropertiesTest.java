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
package org.factcast.server.ui.config;

import static org.assertj.core.api.Assertions.assertThat;

import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.Test;

class ReportStoreConfigurationPropertiesTest {
  private final LogCaptor captor = LogCaptor.forClass(ReportStoreConfigurationProperties.class);
  private final ReportStoreConfigurationProperties uut = new ReportStoreConfigurationProperties();

  @Test
  void warnsIfBothReportStoresAreConfigured() {
    // given
    uut.setPath("path");
    uut.setS3("s3");

    // when
    uut.afterPropertiesSet();

    // then
    assertThat(captor.getWarnLogs())
        .contains(
            "Both properties ‘path‘ and ‘s3‘ are configured under 'factcast.ui.report.store'. This is likely a misconfiguration.");
  }
}
