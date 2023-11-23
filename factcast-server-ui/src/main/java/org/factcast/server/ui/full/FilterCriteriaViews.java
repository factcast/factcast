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

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.util.stream.Stream;
import lombok.NonNull;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.utils.BeanValidationUrlStateBinder;

public class FilterCriteriaViews extends VerticalLayout {
  private final BeanValidationUrlStateBinder<FullQueryBean> binder;
  private final FullQueryBean bean;
  private final FactRepository repo;

  FilterCriteriaViews(
      @NonNull FactRepository repo,
      @NonNull BeanValidationUrlStateBinder<FullQueryBean> binder,
      @NonNull FullQueryBean bean) {
    this.repo = repo;
    this.binder = binder;
    this.bean = bean;

    setWidthFull();
    setMargin(false);
    setPadding(false);
    setSpacing(false);

    add(createButton());
    reset();
  }

  private Component createButton() {
    final var hl = new HorizontalLayout();
    hl.setWidthFull();
    hl.setPadding(false);
    hl.setMargin(false);
    hl.setJustifyContentMode(JustifyContentMode.END);

    final var button = new Button("add Condition", new Icon(VaadinIcon.PLUS_CIRCLE_O));
    button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    button.addClickListener(
        (ComponentEventListener<ClickEvent<Button>>) buttonClickEvent -> createView());

    hl.add(button);

    return hl;
  }

  public void reset() {
    clear();
    createView(false);
  }

  public void clear() {
    getFilterCriteriaViews()
        .forEach(
            fc -> {
              bean.getCriteria().remove(fc.factCriteria());
              fc.removeFromParent();
            });
  }

  public void destroyView(FilterCriteriaView v) {
    bean.getCriteria().remove(v.factCriteria());
    // TODO what about unbinding?
    remove(v.getParent().get());
  }

  public void createView() {
    createView(true);
  }

  public void createView(boolean withRemoveButton) {
    FactCriteria criteria = new FactCriteria();
    bean.getCriteria().add(criteria);
    FilterCriteriaView c = new FilterCriteriaView(repo, binder, criteria);

    if (withRemoveButton)
      addComponentAtIndex(getComponentCount() - 1, wrap(new RemoveButton(this, c), c));
    else addComponentAtIndex(getComponentCount() - 1, wrap(c)); // maybe need style adaptions
  }

  @NonNull
  private static VerticalLayout wrap(Component... c) {
    final var hl = new VerticalLayout(c);
    hl.setWidthFull();
    hl.setSpacing(false);
    hl.setMargin(false);
    hl.setPadding(false);
    hl.addClassNames(
        LumoUtility.Border.BOTTOM,
        LumoUtility.BorderColor.CONTRAST_10,
        LumoUtility.Padding.Bottom.MEDIUM);

    return hl;
  }

  @NonNull
  private Stream<FilterCriteriaView> getFilterCriteriaViews() {
    return getChildren()
        .filter(FilterCriteriaView.class::isInstance)
        .map(f -> (FilterCriteriaView) f);
  }

  public void updateState() {
    getFilterCriteriaViews().forEach(FilterCriteriaView::updateState);
  }
}
