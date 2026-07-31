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
package org.factcast.server.ui.views.filter;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.vaadin.crudui.Crud;
import org.vaadin.crudui.CrudOperation;
import org.vaadin.crudui.data.provider.SimpleBackendDataProvider;
import org.vaadin.crudui.layout.impl.DialogCrudLayout;

@NoCoverageReportToBeGenerated
class MetaButton extends Button {
  private final FactCriteria backingBean;
  private final SimpleBackendDataProvider<MetaTuple> dataProvider;
  private final Crud<MetaTuple> crud;

  MetaButton(FactCriteria backingBean) {
    super("Meta");
    this.backingBean = backingBean;
    dataProvider = new SimpleBackendDataProvider<>(backingBean::getMeta);
    crud =
        Crud.of(MetaTuple.class)
            .layout(DialogCrudLayout.of(MetaTuple.class))
            .onRead(dataProvider)
            .onCreate(metaTuple -> backingBean.getMeta().add(metaTuple))
            .onUpdate(metaTuple -> {})
            .onDelete(metaTuple -> backingBean.getMeta().remove(metaTuple))
            .onSaveSuccess(metaTuple -> updateBadge())
            .onDeleteSuccess(metaTuple -> updateBadge())
            .button(CrudOperation.CREATE)
            .label("Add")
            .build();

    final var dialog = new Dialog("Meta");

    setId("metabox");

    crud.getElement().getStyle().set("width", "100%");

    Button closeButton = new Button("Close");
    closeButton.addClickListener(e -> dialog.close());

    VerticalLayout dialogLayout = new VerticalLayout(crud, closeButton);
    dialogLayout.setPadding(false);
    dialogLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
    dialogLayout.getStyle().set("width", "300px").set("max-width", "100%");
    dialogLayout.setAlignSelf(FlexComponent.Alignment.END, closeButton);
    dialog.add(dialogLayout);

    addClickListener(e -> dialog.open());
  }

  public void update() {
    dataProvider.refreshAll();
    updateBadge();
  }

  private void updateBadge() {
    if (getSuffixComponent() != null) {
      getSuffixComponent().removeFromParent();
    }

    if (backingBean.getMeta().isEmpty()) {
      return;
    }

    Span confirmed = new Span(String.valueOf(backingBean.getMeta().size()));
    confirmed.getElement().getThemeList().add("badge success");
    setSuffixComponent(confirmed);
  }
}
