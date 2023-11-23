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

import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.converter.StringToUuidConverter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.utils.BeanValidationUrlStateBinder;

@NoCoverageReportToBeGenerated
public class FilterCriteriaView extends VerticalLayout {

  private final NameSpacesComboBox ns;
  private final MultiSelectComboBox<String> type;
  private final TextField aggId = new AggregateIdField();

  private final BeanValidationUrlStateBinder<FullQueryBean> binder;
  @Getter private final FactCriteria factCriteria;
  private final List<Binder.Binding<FullQueryBean, ?>> bindings = new ArrayList<>();

  FilterCriteriaView(
      @NonNull FactRepository repo,
      @NonNull BeanValidationUrlStateBinder<FullQueryBean> binder,
      @NonNull FactCriteria factCriteria) {
    ns = new NameSpacesComboBox(repo.namespaces(null));
    this.binder = binder;
    this.factCriteria = factCriteria;
    type = new MultiSelectComboBox<>("Types", Collections.emptyList());
    type.setWidthFull();
    ns.addValueChangeListener(
        event -> {
          updateTypeState();
          if (ns.isEmpty()) {
            type.setValue(new HashSet<>());
          } else {
            type.setItems(repo.types(ns.getValue(), null));
          }
        });

    final var nsAndTypeFilter = new HorizontalLayout(ns, type);
    nsAndTypeFilter.setWidthFull();

    MetaButton metaButton = new MetaButton(factCriteria);
    final var aggIdAndMeta = new HorizontalLayout(aggId, metaButton);
    aggIdAndMeta.setWidthFull();
    aggIdAndMeta.setAlignItems(FlexComponent.Alignment.BASELINE);

    add(nsAndTypeFilter, aggIdAndMeta);

    bind(binder);
    updateTypeState();
    metaButton.update();

    setPadding(false);
  }

  private void bind(BeanValidationUrlStateBinder<FullQueryBean> b) {
    bindings.add(
        b.forField(ns)
            .withNullRepresentation("")
            .asRequired()
            .bind(ignored -> factCriteria.getNs(), (ignored, v) -> factCriteria.setNs(v)));
    bindings.add(
        b.forField(type)
            .withNullRepresentation(new HashSet<>())
            .bind(ignored -> factCriteria.getType(), (ignored, v) -> factCriteria.setType(v)));
    bindings.add(
        b.forField(aggId)
            .withNullRepresentation("")
            .withConverter(new StringToUuidConverter("not a uuid"))
            .bind(ignored -> factCriteria.getAggId(), (ignored, v) -> factCriteria.setAggId(v)));
  }

  private void updateTypeState() {
    type.setEnabled(!ns.isEmpty());
  }

  public void removeBindings() {
    bindings.forEach(binder::removeBinding);
  }
}
