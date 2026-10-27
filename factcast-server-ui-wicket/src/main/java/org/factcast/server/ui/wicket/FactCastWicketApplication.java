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

import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.factcast.server.ui.plugins.JsonViewPluginService;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.port.ReportStore;

/** Services live in the application, never in serialized pages. */
public class FactCastWicketApplication extends WebApplication {
  private final FactRepository repository;
  private final ReportStore reports;
  private final JsonViewPluginService plugins;

  public FactCastWicketApplication(
      FactRepository repository, ReportStore reports, JsonViewPluginService plugins) {
    this.repository = repository;
    this.reports = reports;
    this.plugins = plugins;
  }

  @Override
  public Class<? extends Page> getHomePage() {
    return QueryPage.class;
  }

  @Override
  protected void init() {
    super.init();
    getRequestCycleListeners()
        .add(
            new org.apache.wicket.request.cycle.IRequestCycleListener() {
              @Override
              public void onRequestHandlerResolved(
                  org.apache.wicket.request.cycle.RequestCycle cycle,
                  org.apache.wicket.request.IRequestHandler handler) {
                if (handler
                        instanceof
                        org.apache.wicket.core.request.handler.IPageRequestHandler pageHandler
                    && pageHandler.getPage() instanceof BasePage page) {
                  page.authorizeRequest();
                }
              }
            });
    getMarkupSettings().setStripWicketTags(true);
    mountPage("/ui/full", QueryPage.class);
    mountPage("/ui/id", IdPage.class);
    mountPage("/ui/report", ReportPage.class);
    mountPage("/login", LoginPage.class);
    mountResource("/assets/ui.css", new PackageResourceReference(BasePage.class, "ui.css"));
    mountResource("/assets/ui.js", new PackageResourceReference(BasePage.class, "ui.js"));
  }

  @Override
  protected org.apache.wicket.IConverterLocator newConverterLocator() {
    var converters = new org.apache.wicket.ConverterLocator();
    converters.set(
        java.util.UUID.class,
        new org.apache.wicket.util.convert.IConverter<java.util.UUID>() {
          @Override
          public java.util.UUID convertToObject(String value, java.util.Locale locale) {
            if (value == null || value.isBlank()) return null;
            try {
              return java.util.UUID.fromString(value);
            } catch (IllegalArgumentException e) {
              throw new org.apache.wicket.util.convert.ConversionException("Enter a valid UUID.");
            }
          }

          @Override
          public String convertToString(java.util.UUID value, java.util.Locale locale) {
            return value.toString();
          }
        });
    return converters;
  }

  static FactCastWicketApplication current() {
    return (FactCastWicketApplication) WebApplication.get();
  }

  FactRepository repository() {
    return repository;
  }

  ReportStore reports() {
    return reports;
  }

  JsonViewPluginService plugins() {
    return plugins;
  }
}
