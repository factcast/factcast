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

import java.util.List;
import org.apache.wicket.markup.head.*;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.*;
import org.apache.wicket.model.*;
import org.apache.wicket.request.http.handler.RedirectRequestHandler;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.factcast.server.ui.report.*;

public class ReportPage extends BasePage {
  private final ReportFilterBean bean;
  private final IModel<String> name = Model.of("");
  private boolean saveState;

  public ReportPage(PageParameters parameters) {
    super("Report");
    bean = new ReportFilterBean(app().repository().latestSerial());
    try {
      QueryState.restore(parameters, bean);
    } catch (IllegalArgumentException e) {
      bean.reset();
      error(e.getMessage());
    }
    var form = new Form<Void>("form");
    form.add(new CsrfField());
    var filters = new FilterPanel("filters", bean);
    form.add(filters);
    form.add(new TextField<>("name", name));
    form.add(
        new Button("generate") {
          @Override
          public void onSubmit() {
            generate();
          }
        });
    form.add(
        new Link<Void>("reset") {
          @Override
          public void onClick() {
            bean.reset();
            filters.rebuild();
            name.setObject("");
            form.clearInput();
            saveState = true;
          }
        });
    form.setDefaultButton((Button) form.get("generate"));
    add(form);
    add(
        new Link<Void>("refresh") {
          @Override
          public void onClick() {}
        });
    add(
        new ListView<ReportEntry>(
            "reportsList",
            new LoadableDetachableModel<List<ReportEntry>>() {
              @Override
              protected List<ReportEntry> load() {
                return app().reports().listAllForUser(userName());
              }
            }) {
          @Override
          protected void populateItem(ListItem<ReportEntry> item) {
            String filename = item.getModelObject().name();
            item.add(new Label("name", filename));
            item.add(new Label("created", item.getModelObject().lastChanged()));
            item.add(
                new Link<Void>("download") {
                  @Override
                  public void onClick() {
                    try {
                      getRequestCycle()
                          .scheduleRequestHandlerAfterCurrent(
                              new RedirectRequestHandler(
                                  app()
                                      .reports()
                                      .getReportDownload(userName(), filename)
                                      .toExternalForm()));
                    } catch (Exception e) {
                      failure(e);
                    }
                  }
                });
            var deletion = new Form<Void>("deleteForm");
            deletion.add(new CsrfField());
            deletion.add(
                new Button("delete") {
                  @Override
                  public void onSubmit() {
                    try {
                      app().reports().delete(userName(), filename);
                      info("Report deleted.");
                    } catch (Exception e) {
                      failure(e);
                    }
                  }
                });
            item.add(deletion);
          }
        });
  }

  private void generate() {
    String filename = name.getObject();
    boolean created = false;
    try {
      if (filename == null || filename.isBlank())
        throw new IllegalArgumentException("Enter a report file name.");
      filename = filename.replaceFirst("\\.json$", "");
      if (!filename.matches("[a-zA-Z0-9_-]+"))
        throw new IllegalArgumentException(
            "Use letters, numbers, hyphens, and underscores in the report name.");
      filename += ".json";
      QueryState.validate(bean);
      long count;
      var plugins = app().plugins();
      try (var stream = app().reports().createBatchUpload(userName(), filename, bean)) {
        created = true;
        count =
            app()
                .repository()
                .fetchAndProcessAll(
                    bean, fact -> stream.writeToBatch(plugins.process(fact).fact()));
      }
      if (count == 0) {
        app().reports().delete(userName(), filename);
        warn("No data was found for this query. No report was created.");
      } else {
        info("Report generated: " + count + " facts.");
        name.setObject("");
      }
      saveState = true;
    } catch (Exception e) {
      if (created) {
        try {
          app().reports().delete(userName(), filename);
        } catch (Exception cleanup) {
          e.addSuppressed(cleanup);
        }
      }
      failure(e);
    }
  }

  @Override
  public void renderHead(IHeaderResponse response) {
    super.renderHead(response);
    if (saveState) {
      response.render(OnDomReadyHeaderItem.forScript(QueryState.script(QueryState.json(bean))));
      saveState = false;
    }
  }
}
