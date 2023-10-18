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
package org.factcast.server.ui.config;

import com.vaadin.flow.spring.security.AuthenticationContext;
import java.util.*;
import java.util.stream.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.server.security.auth.FactCastUser;

@RequiredArgsConstructor
public class SecurityService {

  private final AuthenticationContext authenticationContext;

  private FactCastUser getAuthenticatedUser() {
    return authenticationContext.getAuthenticatedUser(FactCastUser.class).orElseThrow();
  }

  public void logout() {
    authenticationContext.logout();
  }

  public final boolean canRead(@NonNull Fact f) {
    return canRead(f.ns());
  }

  public final boolean canRead(@NonNull FactSpec f) {
    return canRead(f.ns());
  }

  public boolean canRead(@NonNull String ns) {
    return getAuthenticatedUser().canRead(ns);
  }

  public final Set<FactSpec> filterReadable(@NonNull Collection<FactSpec> all) {
    return all.stream().filter(fs -> canRead(fs.ns())).collect(Collectors.toSet());
  }
}
