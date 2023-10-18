/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.ui.security;

import static org.mockito.Mockito.*;

import com.vaadin.flow.spring.security.AuthenticationContext;
import java.util.*;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.server.security.auth.FactCastUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
public class DefaultSecurityServiceTest {

  @Mock
  @MockitoSettings(strictness = Strictness.LENIENT)
  AuthenticationContext authenticationContext;

  @Mock
  @MockitoSettings(strictness = Strictness.LENIENT)
  FactCastUser user;

  @InjectMocks private DefaultSecurityService underTest;

  void setupPermissionCheck() {
    when(user.canRead(anyString()))
        .thenAnswer(
            i -> {
              try {
                Integer.valueOf(i.getArgument(0));
                return true;
              } catch (NumberFormatException nfe) {
                return false;
              }
            });
    when(authenticationContext.getAuthenticatedUser(any())).thenReturn(Optional.of(user));
  }

  @Nested
  class WhenLogouting {

    @Test
    void delegates() {
      underTest.logout();
      verify(authenticationContext).logout();
    }
  }

  @Nested
  class WhenCheckingIfCanReadFact {
    @BeforeEach
    void setup() {
      setupPermissionCheck();
    }

    @Test
    void delegates() {
      Assertions.assertThat(underTest.canRead(Fact.builder().ns("1").buildWithoutPayload()))
          .isTrue();
      Assertions.assertThat(underTest.canRead(Fact.builder().ns("a").buildWithoutPayload()))
          .isFalse();
    }
  }

  @Nested
  class WhenCheckingIfCanReadNs {
    @BeforeEach
    void setup() {
      setupPermissionCheck();
    }

    @Test
    void delegates() {
      Assertions.assertThat(underTest.canRead(("1"))).isTrue();
      Assertions.assertThat(underTest.canRead(("a"))).isFalse();
    }
  }

  @Nested
  class WhenCheckingIfCanReadFactSpec {
    @BeforeEach
    void setup() {
      setupPermissionCheck();
    }

    @Test
    void delegates() {
      Assertions.assertThat(underTest.canRead(FactSpec.ns("1"))).isTrue();
      Assertions.assertThat(underTest.canRead(FactSpec.ns("a"))).isFalse();
    }
  }

  @Nested
  class WhenFilteringReadable {
    @BeforeEach
    void setup() {
      setupPermissionCheck();
    }

    @Test
    void filters() {
      var specs = List.of(FactSpec.ns("1"), FactSpec.ns("2"), FactSpec.ns("a"), FactSpec.ns("3"));
      Assertions.assertThat(underTest.filterReadable(specs)).isNotEmpty().hasSize(3);
    }
  }
}
