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

import java.math.BigDecimal;
import java.util.*;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.extensions.markup.html.form.datetime.LocalDateTextField;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.list.*;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.*;
import org.apache.wicket.validation.validator.RangeValidator;
import org.factcast.server.ui.full.FullFilterBean;
import org.factcast.server.ui.views.filter.*;

/** Form controls shared by the query and report screens. */
class FilterPanel extends Panel {
  private final AbstractFilterBean bean;

  FilterPanel(String id, AbstractFilterBean bean) {
    super(id);
    this.bean = bean;
    setOutputMarkupId(true);
    add(
        new ListView<FactCriteria>("conditions", new PropertyModel<>(bean, "criteria")) {
          @Override
          protected void populateItem(ListItem<FactCriteria> item) {
            var criteria = item.getModelObject();
            var types =
                new ListMultipleChoice<String>(
                    "types",
                    new IModel<Collection<String>>() {
                      @Override
                      public Collection<String> getObject() {
                        return criteria.getType();
                      }

                      @Override
                      public void setObject(Collection<String> selected) {
                        criteria.setType(
                            selected == null || selected.isEmpty()
                                ? null
                                : new HashSet<>(selected));
                      }
                    },
                    new LoadableDetachableModel<List<String>>() {
                      @Override
                      protected List<String> load() {
                        return criteria.getNs() == null
                            ? List.of()
                            : FactCastWicketApplication.current()
                                .repository()
                                .types(criteria.getNs(), null);
                      }
                    }) {
                  @Override
                  protected void onConfigure() {
                    super.onConfigure();
                    setEnabled(criteria.getNs() != null);
                  }
                };
            types.setOutputMarkupId(true);
            var namespace =
                new DropDownChoice<String>(
                    "namespace",
                    new PropertyModel<>(criteria, "ns"),
                    new LoadableDetachableModel<List<String>>() {
                      @Override
                      protected List<String> load() {
                        return FactCastWicketApplication.current().repository().namespaces(null);
                      }
                    });
            namespace.setNullValid(true);
            namespace.add(
                new OnChangeAjaxBehavior() {
                  @Override
                  protected void onUpdate(AjaxRequestTarget target) {
                    criteria.setType(null);
                    types.clearInput();
                    target.add(types);
                  }
                });
            item.add(namespace, types);
            item.add(
                new TextField<>("aggregateId", new PropertyModel<>(criteria, "aggId"), UUID.class));
            item.add(
                new ListView<MetaTuple>("metadata", new PropertyModel<>(criteria, "meta")) {
                  @Override
                  protected void populateItem(ListItem<MetaTuple> tuple) {
                    tuple.add(
                        new TextField<>("key", new PropertyModel<>(tuple.getModelObject(), "key")));
                    tuple.add(
                        new TextField<>(
                            "value", new PropertyModel<>(tuple.getModelObject(), "value")));
                    tuple.add(
                        new Button("removeMeta") {
                          @Override
                          public void onSubmit() {
                            criteria.getMeta().remove(tuple.getIndex());
                            rebuild();
                          }
                        });
                  }
                }.setReuseItems(true));
            item.add(
                new Button("addMeta") {
                  @Override
                  public void onSubmit() {
                    criteria.getMeta().add(new MetaTuple());
                    rebuild();
                  }
                });
            item.add(
                new Button("remove") {
                  @Override
                  public void onSubmit() {
                    bean.getCriteria().remove(item.getIndex());
                    rebuild();
                  }

                  @Override
                  protected void onConfigure() {
                    super.onConfigure();
                    setEnabled(bean.getCriteria().size() > 1);
                  }
                });
          }
        }.setReuseItems(true));
    add(
        new Button("add") {
          @Override
          public void onSubmit() {
            bean.getCriteria().add(new FactCriteria());
            rebuild();
          }
        });
    add(new TextField<>("from", new PropertyModel<>(bean, "from"), BigDecimal.class));
    add(new TextField<>("to", new PropertyModel<>(bean, "to"), BigDecimal.class));
    add(new LocalDateTextField("since", new PropertyModel<>(bean, "since"), "yyyy-MM-dd"));
    add(new LocalDateTextField("until", new PropertyModel<>(bean, "until"), "yyyy-MM-dd"));
    add(
        new Button("startDate") {
          @Override
          public void onSubmit() {
            if (bean.getSince() != null)
              bean.setFrom(
                  BigDecimal.valueOf(
                      FactCastWicketApplication.current()
                          .repository()
                          .lastSerialBefore(bean.getSince())
                          .orElse(0)));
          }
        });
    add(
        new Button("endDate") {
          @Override
          public void onSubmit() {
            bean.setTo(
                bean.getUntil() == null
                    ? null
                    : FactCastWicketApplication.current()
                        .repository()
                        .firstSerialAfter(bean.getUntil())
                        .map(BigDecimal::valueOf)
                        .orElse(null));
          }
        });
    add(
        new Button("scratch") {
          @Override
          public void onSubmit() {
            bean.setFrom(BigDecimal.ZERO);
          }
        });
    add(
        new Button("latest") {
          @Override
          public void onSubmit() {
            bean.setFrom(
                BigDecimal.valueOf(
                    FactCastWicketApplication.current().repository().latestSerial()));
          }
        });
    add(
        new Button("clearEnd") {
          @Override
          public void onSubmit() {
            bean.setTo(null);
          }
        });
    var paging = new org.apache.wicket.markup.html.WebMarkupContainer("paging");
    paging.setVisible(bean instanceof FullFilterBean);
    if (bean instanceof FullFilterBean full) {
      paging.add(
          new TextField<>("limit", new PropertyModel<Integer>(full, "limit"), Integer.class)
              .add(RangeValidator.range(1, 1000)));
      paging.add(
          new TextField<>("offset", new PropertyModel<Integer>(full, "offset"), Integer.class)
              .add(RangeValidator.range(0, 1000)));
    } else {
      paging.add(new TextField<>("limit"), new TextField<>("offset"));
    }
    add(paging);
  }

  void rebuild() {
    ((ListView<?>) get("conditions")).removeAll();
  }
}
