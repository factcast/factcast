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
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.markup.head.*;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.model.*;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.factcast.server.ui.id.IdQueryBean;
import org.factcast.server.ui.plugins.JsonViewEntries;

public class IdPage extends BasePage {
  private final IdQueryBean bean = new IdQueryBean();
  private final List<Integer> versions = new ArrayList<>(List.of(0));
  private final JsonPanel results;
  private boolean saveState;

  public IdPage(PageParameters parameters) {
    super("Query by Fact-ID");
    try {
      QueryState.restore(parameters, bean);
    } catch (IllegalArgumentException e) {
      bean.setId(null);
      bean.setVersion(0);
      error(e.getMessage());
    }
    if (bean.getVersion() != 0) versions.add(bean.getVersion());
    var form = new Form<Void>("form");
    form.add(new CsrfField());
    var version =
        new DropDownChoice<Integer>(
            "version",
            new PropertyModel<>(bean, "version"),
            versions,
            new IChoiceRenderer<Integer>() {
              @Override
              public Object getDisplayValue(Integer value) {
                return value == 0 ? "As published" : value.toString();
              }
            }) {
          @Override
          protected void onConfigure() {
            super.onConfigure();
            setEnabled(versions.size() > 1);
          }
        };
    version.setOutputMarkupId(true);
    form.add(version);
    var id = new TextField<UUID>("id", new PropertyModel<>(bean, "id"), UUID.class);
    id.setRequired(true);
    id.add(
        new OnChangeAjaxBehavior() {
          @Override
          protected void onUpdate(AjaxRequestTarget target) {
            bean.setVersion(0);
            versions.clear();
            versions.add(0);
            try {
              app()
                  .repository()
                  .findBy(bean)
                  .ifPresent(
                      fact -> versions.addAll(app().repository().versions(fact.ns(), fact.type())));
              version.modelChanged();
            } catch (Exception e) {
              failure(e);
            }
            target.add(version);
          }
        });
    form.add(id);
    results = new JsonPanel("results", null);
    add(results);
    form.add(
        new Button("query") {
          @Override
          public void onSubmit() {
            try {
              QueryState.validate(bean);
              var fact = app().repository().findBy(bean);
              if (fact.isPresent()) {
                var available = app().repository().versions(fact.get().ns(), fact.get().type());
                versions.clear();
                versions.add(0);
                versions.addAll(available);
                results.show(new JsonViewEntries(List.of(app().plugins().process(fact.get()))), 0);
              } else {
                results.clear();
                warn("Fact not found");
              }
              saveState = true;
            } catch (Exception e) {
              results.clear();
              failure(e);
            }
          }
        });
    form.add(
        new Link<Void>("reset") {
          @Override
          public void onClick() {
            bean.setId(null);
            bean.setVersion(0);
            versions.clear();
            versions.add(0);
            form.clearInput();
            results.clear();
            saveState = true;
          }
        });
    add(form);
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
