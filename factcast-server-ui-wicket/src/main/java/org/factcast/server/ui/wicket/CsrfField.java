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

import jakarta.servlet.http.HttpServletRequest;
import org.apache.wicket.markup.html.form.HiddenField;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.cycle.RequestCycle;
import org.springframework.security.web.csrf.CsrfToken;

/** Spring Security validates both regular submissions and Wicket Ajax requests. */
class CsrfField extends HiddenField<String> {
  CsrfField() {
    super("csrf", Model.of(""));
  }

  static CsrfToken token() {
    Object request = RequestCycle.get().getRequest().getContainerRequest();
    return request instanceof HttpServletRequest servlet
        ? (CsrfToken) servlet.getAttribute(CsrfToken.class.getName())
        : null;
  }

  @Override
  public String getInputName() {
    var token = token();
    return token == null ? "_csrf" : token.getParameterName();
  }

  @Override
  protected void onBeforeRender() {
    var token = token();
    setModelObject(token == null ? "" : token.getToken());
    super.onBeforeRender();
  }
}
