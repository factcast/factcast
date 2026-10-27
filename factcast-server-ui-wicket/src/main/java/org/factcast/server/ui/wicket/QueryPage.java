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

import org.apache.wicket.markup.head.*;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.link.*;
import org.apache.wicket.request.handler.resource.ResourceStreamRequestHandler;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.resource.StringResourceStream;
import org.factcast.server.ui.full.FullFilterBean;

public class QueryPage extends BasePage {
  private final FullFilterBean bean;
  private final JsonPanel results;
  private String exportJson;
  private boolean saveState;

  public QueryPage(PageParameters parameters) {
    super("Query");
    bean = new FullFilterBean(app().repository().latestSerial());
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
    results = new JsonPanel("results", this::applyQuickFilter);
    add(results);
    form.add(
        new Button("query") {
          @Override
          public void onSubmit() {
            runQuery();
          }
        });
    form.add(
        new Link<Void>("reset") {
          @Override
          public void onClick() {
            bean.reset();
            filters.rebuild();
            form.clearInput();
            exportJson = null;
            results.clear();
            saveState = true;
          }
        });
    form.add(
        new Link<Void>("export") {
          @Override
          protected void onConfigure() {
            super.onConfigure();
            setVisible(exportJson != null);
          }

          @Override
          public void onClick() {
            getRequestCycle()
                .scheduleRequestHandlerAfterCurrent(
                    new ResourceStreamRequestHandler(
                        new StringResourceStream(exportJson, "application/json"), "events.json"));
          }
        });
    form.setDefaultButton((Button) form.get("query"));
    add(form);
  }

  private void runQuery() {
    try {
      QueryState.validate(bean);
      var facts = app().repository().fetchChunk(bean);
      var entries = app().plugins().process(facts);
      exportJson = entries.json();
      results.show(entries, bean.getCriteria().size());
      saveState = true;
      if (facts.isEmpty()) info("No facts found.");
    } catch (Exception e) {
      failure(e);
    }
  }

  private void applyQuickFilter(JsonPanel.QuickFilter option) {
    if (option.criteria() < 0 || option.criteria() >= bean.getCriteria().size()) {
      error("Select an existing condition.");
      return;
    }
    var criteria = bean.getCriteria().get(option.criteria());
    if (option.aggregateId() != null) criteria.setAggId(option.aggregateId());
    if (option.key() != null) {
      var meta = new org.factcast.server.ui.views.filter.MetaTuple();
      meta.setKey(option.key());
      meta.setValue(option.value());
      criteria.getMeta().removeIf(existing -> option.key().equals(existing.getKey()));
      criteria.getMeta().add(meta);
    }
    ((FilterPanel) get("form:filters")).rebuild();
    runQuery();
  }

  @Override
  protected void onBeforeRender() {
    results.setConditionCount(bean.getCriteria().size());
    super.onBeforeRender();
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
