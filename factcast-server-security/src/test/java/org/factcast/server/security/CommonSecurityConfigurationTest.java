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
package org.factcast.server.security;

import static org.assertj.core.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.factcast.server.security.auth.FactCastAccessConfiguration;
import org.factcast.server.security.auth.FactCastSecretProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class CommonSecurityConfigurationTest {

  @Test
  void missingSecretIsLoggedAsErrorAndOtherAccountsRemainUsable() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(CommonSecurityConfiguration.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      FactCastSecretProperties secrets = new FactCastSecretProperties();
      secrets.getSecrets().put("configured-account", "password");
      CommonSecurityConfiguration configuration = new CommonSecurityConfiguration();

      FactCastAccessConfiguration access = configuration.authenticationConfig(secrets);

      assertThat(appender.list)
          .anySatisfy(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage())
                    .isEqualTo("Missing secret for account: 'account-without-secret'");
              });

      var users =
          configuration.userDetailsService(access, secrets, configuration.passwordEncoder());
      assertThat(users.loadUserByUsername("configured-account").getPassword())
          .isEqualTo("password");
      assertThatThrownBy(() -> users.loadUserByUsername("account-without-secret"))
          .isInstanceOf(UsernameNotFoundException.class);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
