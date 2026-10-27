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

import java.util.*;
import java.util.stream.Collectors;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.server.security.auth.FactCastUser;
import org.factcast.server.ui.security.SecurityService;
import org.springframework.security.core.context.SecurityContextHolder;

class WicketSecurityService implements SecurityService {
  @Override
  public FactCastUser getAuthenticatedUser() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof FactCastUser user)
      return user;
    throw new org.springframework.security.authentication
        .AuthenticationCredentialsNotFoundException("Please log in");
  }

  @Override
  public boolean canRead(String namespace) {
    return getAuthenticatedUser().canRead(namespace);
  }

  @Override
  public boolean canRead(Fact fact) {
    return canRead(fact.ns());
  }

  @Override
  public boolean canRead(FactSpec spec) {
    return canRead(spec.ns());
  }

  @Override
  public Set<FactSpec> filterReadable(Collection<FactSpec> specs) {
    return specs.stream().filter(this::canRead).collect(Collectors.toSet());
  }

  @Override
  public void logout() {
    var request =
        (jakarta.servlet.http.HttpServletRequest)
            org.apache.wicket.request.cycle.RequestCycle.get().getRequest().getContainerRequest();
    var response =
        (jakarta.servlet.http.HttpServletResponse)
            org.apache.wicket.request.cycle.RequestCycle.get().getResponse().getContainerResponse();
    new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
        .logout(request, response, SecurityContextHolder.getContext().getAuthentication());
  }
}
