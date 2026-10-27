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

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.head.*;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.CssResourceReference;

public class LoginPage extends WebPage {
  public LoginPage(PageParameters parameters) {
    var form = new WebMarkupContainer("login");
    form.add(AttributeModifier.replace("action", BasePage.contextPath() + "/login"));
    form.add(new CsrfField());
    add(form);
    add(
        new Label(
                "message", parameters.get("error").isNull() ? "" : "Invalid username or password.")
            .setVisible(!parameters.get("error").isNull()));
  }

  @Override
  public void renderHead(IHeaderResponse response) {
    response.render(CssHeaderItem.forReference(new CssResourceReference(BasePage.class, "ui.css")));
  }
}
