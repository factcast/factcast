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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import lombok.NonNull;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.utils.BeanValidationUrlStateBinder;

@NoCoverageReportToBeGenerated
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

    addViewsAccordingTo(bean);
  }

  private void addViewsAccordingTo(@NonNull FullQueryBean bean) {
    AtomicBoolean first = new AtomicBoolean(true);
    bean.getCriteria().forEach(c -> addFilterCriteriaView(!first.getAndSet(false), c));
    binder.readBean(bean);
  }

  private Component createButton() {
    final var hl = new HorizontalLayout();
    hl.setWidthFull();
    hl.setPadding(false);
    hl.setMargin(false);
    hl.setJustifyContentMode(JustifyContentMode.END);

    final var button = new Button("add Condition", new Icon(VaadinIcon.PLUS_CIRCLE_O));
    button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    button.addClickListener(buttonClickEvent -> addCondition());

    hl.add(button);

    return hl;
  }

  private void removeCondition(@NonNull FilterCriteriaView v) {
    v.getParent().ifPresent(this::remove); // it was wrapped by a container
    v.removeBindings();
  }

  private void removeConditionAndBackingBean(@NonNull FilterCriteriaView v) {
    removeCondition(v);
    bean.getCriteria().remove(v.factCriteria());
  }

  private void addCondition() {
    FactCriteria criteria = new FactCriteria();
    bean.getCriteria().add(criteria);
    addFilterCriteriaView(true, criteria);
  }

  private void addFilterCriteriaView(boolean withRemoveButton, @NonNull FactCriteria criteria) {
    FilterCriteriaView c = new FilterCriteriaView(repo, binder, criteria);

    if (withRemoveButton)
      addComponentAtIndex(
          getComponentCount() - 1,
          new FilterCriteriaViewContainer(
              c, new RemoveButton(() -> removeConditionAndBackingBean(c))));
    else
      addComponentAtIndex(
          getComponentCount() - 1,
          new FilterCriteriaViewContainer(c)); // maybe need style adaptions
  }

  @NonNull
  private Stream<FilterCriteriaView> getFilterCriteriaViews() {
    return getChildren()
        .filter(FilterCriteriaViewContainer.class::isInstance)
        .map(f -> ((FilterCriteriaViewContainer) f).filterCriteriaView());
  }

  public void rebuild() {
    getFilterCriteriaViews().forEach(this::removeCondition);
    addViewsAccordingTo(bean);
  }
}
