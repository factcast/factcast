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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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

    add(createButton());
    reset();
  }

  private Button createButton() {
    Button button = new Button("add Condition");
    button.addClickListener(buttonClickEvent -> createView());
    return button;
  }

  public void reset() {
    clear();
    createView(false);
  }

  public void clear() {
    getFilterCriteriaViews().forEach(this::destroyView);
  }

  public void destroyView(@NonNull FilterCriteriaView v) {
    v.removeBindings();
    // remove backing bean
    bean.getCriteria().remove(v.factCriteria());
    remove(v.getParent().get()); // it was wrapped by a container
  }

  public void createView() {
    createView(true);
  }

  public void createView(boolean withRemoveButton) {
    FactCriteria criteria = new FactCriteria();
    bean.getCriteria().add(criteria);
    FilterCriteriaView c = new FilterCriteriaView(repo, binder, criteria);

    if (withRemoveButton)
      addComponentAtIndex(
          getComponentCount() - 1, new FilterCriteriaViewContainer(c, new RemoveButton(this, c)));
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

  public void updateState() {
    getFilterCriteriaViews().forEach(FilterCriteriaView::updateState);
  }
}
