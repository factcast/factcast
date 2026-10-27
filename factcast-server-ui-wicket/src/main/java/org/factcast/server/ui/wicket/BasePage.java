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

import org.apache.wicket.RestartResponseException;
import org.apache.wicket.markup.head.*;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.request.resource.*;
import org.factcast.server.security.auth.FactCastUser;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class BasePage extends WebPage {
  private final String owner;

  protected BasePage(String title) {
    if (!(SecurityContextHolder.getContext().getAuthentication() != null
        && SecurityContextHolder.getContext().getAuthentication().getPrincipal()
            instanceof FactCastUser)) {
      throw new RestartResponseException(LoginPage.class);
    }
    owner = userName();
    add(new Label("title", title));
    add(new BookmarkablePageLink<>("query", QueryPage.class));
    add(new BookmarkablePageLink<>("id", IdPage.class));
    add(new BookmarkablePageLink<>("reports", ReportPage.class));
    add(new FeedbackPanel("feedback"));
    var logout = new org.apache.wicket.markup.html.WebMarkupContainer("logout");
    logout.add(org.apache.wicket.AttributeModifier.replace("action", contextPath() + "/logout"));
    logout.add(new CsrfField());
    add(logout);
    var unavailable = app().plugins().getNonResponsivePlugins();
    if (!unavailable.isEmpty())
      warn("Some plugins are not yet initialized: " + String.join(", ", unavailable));
  }

  /** Called before Wicket dispatches page callbacks, including export downloads. */
  void authorizeRequest() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof FactCastUser)) {
      throw new RestartResponseException(LoginPage.class);
    }
    if (!owner.equals(authentication.getName())) {
      throw new RestartResponseException(QueryPage.class);
    }
  }

  static String contextPath() {
    return ((org.apache.wicket.protocol.http.servlet.ServletWebRequest)
            org.apache.wicket.request.cycle.RequestCycle.get().getRequest())
        .getContextPath();
  }

  protected final FactCastWicketApplication app() {
    return FactCastWicketApplication.current();
  }

  protected final String userName() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  protected void failure(Exception exception) {
    org.slf4j.LoggerFactory.getLogger(getClass()).warn("UI operation failed", exception);
    error(
        exception instanceof IllegalArgumentException
            ? exception.getMessage()
            : "The operation failed. Please check your input and try again.");
  }

  @Override
  public void renderHead(IHeaderResponse response) {
    super.renderHead(response);
    response.render(CssHeaderItem.forReference(new CssResourceReference(BasePage.class, "ui.css")));
    response.render(
        JavaScriptHeaderItem.forReference(
            new JavaScriptResourceReference(BasePage.class, "ui.js")));
  }
}
