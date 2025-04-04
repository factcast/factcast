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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.NonNull;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.utils.BeanValidationUrlStateBinder;

@NoCoverageReportToBeGenerated
public class FilterCriteriaViews<F extends FilterBean> extends VerticalLayout {
  private final BeanValidationUrlStateBinder<F> binder;
  private final F bean;
  private final transient FactRepository repo;

  private final List<FilterCriteriaCountUpdateListener> updateListeners = new ArrayList<>();

  public FilterCriteriaViews(
      @NonNull FactRepository repo,
      @NonNull BeanValidationUrlStateBinder<F> binder,
      @NonNull F bean) {
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

  private void addViewsAccordingTo(@NonNull F bean) {
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
    hl.addClassNames(LumoUtility.Border.TOP, LumoUtility.BorderColor.CONTRAST_20);

    final var button = new Button("add Condition", new Icon(VaadinIcon.PLUS_CIRCLE_O));
    button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    button.addClickListener(buttonClickEvent -> addCondition());

    hl.add(button);

    return hl;
  }

  private void removeCondition(@NonNull FilterCriteriaView<F> v) {
    v.getParent().ifPresent(this::remove);
    v.removeBindings();
  }

  private void removeConditionAndBackingBean(@NonNull FilterCriteriaView<F> v) {
    final var oldCount = bean.getCriteria().size();
    removeCondition(v);
    bean.getCriteria().remove(v.factCriteria());
    final var newCount = bean.getCriteria().size();

    updateListeners.forEach(ul -> ul.onFilterCriteriaCountChanged(oldCount, newCount));
  }

  private void addCondition() {
    final var criteria = new FactCriteria();
    final var oldCount = bean.getCriteria().size();
    bean.getCriteria().add(criteria);
    addFilterCriteriaView(true, criteria);
    final var newCount = bean.getCriteria().size();

    updateListeners.forEach(ul -> ul.onFilterCriteriaCountChanged(oldCount, newCount));
  }

  private void addFilterCriteriaView(boolean withRemoveButton, @NonNull FactCriteria criteria) {
    FilterCriteriaView<F> c = new FilterCriteriaView<>(repo, binder, criteria);
    Supplier<Integer> indexSupplier = () -> bean.getCriteria().indexOf(criteria);

    FilterCriteriaViewContainer viewContainer;
    if (withRemoveButton) {
      viewContainer =
          new FilterCriteriaViewContainer(
              c,
              indexSupplier,
              v -> {
                removeFilterCriteriaCountUpdateListener(v);
                removeConditionAndBackingBean(c);
              });
    } else {
      viewContainer = new FilterCriteriaViewContainer(c, indexSupplier);
    }

    addComponentAtIndex(getComponentCount() - 1, viewContainer);
    addFilterCriteriaCountUpdateListener(viewContainer);
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

  public void addFilterCriteriaCountUpdateListener(FilterCriteriaCountUpdateListener listener) {
    updateListeners.add(listener);
  }

  public void removeFilterCriteriaCountUpdateListener(FilterCriteriaCountUpdateListener listener) {
    updateListeners.remove(listener);
  }

  public interface FilterCriteriaCountUpdateListener {
    void onFilterCriteriaCountChanged(int oldCount, int newCount);
  }
}
