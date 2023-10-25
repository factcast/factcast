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
package org.factcast.server.ui.full;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.util.Collection;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.vaadin.crudui.crud.CrudListener;
import org.vaadin.crudui.crud.impl.GridCrud;

@NoCoverageReportToBeGenerated
class MetaButton extends Button {
  private final FullQueryBean backingBean;
  private final GridCrud<MetaTuple> crud = new GridCrud<>(MetaTuple.class);

  MetaButton(FullQueryBean backingBean) {
    super("Meta");
    this.backingBean = backingBean;

    final var dialog = new Dialog("Meta");

    crud.setCrudListener(new MetaTupleCrudListener());
    crud.getCrudFormFactory().setUseBeanValidation(true);
    setId("metabox");

    crud.setWidthFull();

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
    if (getSuffixComponent() != null) {
      getSuffixComponent().removeFromParent();
    }

    if (backingBean.getMeta().isEmpty()) {
      return;
    }

    Span confirmed = new Span(String.valueOf(backingBean.getMeta().size()));
    confirmed.getElement().getThemeList().add("badge success");
    setSuffixComponent(confirmed);

    crud.refreshGrid();
  }

  @NoCoverageReportToBeGenerated
  class MetaTupleCrudListener implements CrudListener<MetaTuple> {

    @Override
    public Collection<MetaTuple> findAll() {
      return backingBean.getMeta();
    }

    @Override
    public MetaTuple add(MetaTuple user) {
      // should not be directly pushed into the formbean, TODO learn about grids and
      // binding
      backingBean.getMeta().add(user);
      MetaButton.this.update();
      return user;
    }

    @Override
    public MetaTuple update(MetaTuple user) {
      return user;
    }

    @Override
    public void delete(MetaTuple user) {
      // should not be directly removed from the formbean, TODO learn about grids and
      // binding
      backingBean.getMeta().remove(user);
      MetaButton.this.update();
    }
  }
}
