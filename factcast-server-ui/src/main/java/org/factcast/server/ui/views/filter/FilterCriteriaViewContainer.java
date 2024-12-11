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
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.util.NoCoverageReportToBeGenerated;

import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("java:S1948")
@Getter
@NoCoverageReportToBeGenerated
public class FilterCriteriaViewContainer extends HorizontalLayout
    implements FilterCriteriaViews.FilterCriteriaCountUpdateListener {
  private final FilterCriteriaView filterCriteriaView;
  private final Supplier<Integer> indexSupplier;
  private final Paragraph text;

  public FilterCriteriaViewContainer(
      @NonNull FilterCriteriaView c, @NonNull Supplier<Integer> indexSupplier) {
    this(c, indexSupplier, null);
  }

  public FilterCriteriaViewContainer(
      @NonNull FilterCriteriaView filterCriteriaView,
      @NonNull Supplier<Integer> indexSupplier,
      Consumer<FilterCriteriaViewContainer> onRemoveClick) {
    this.filterCriteriaView = filterCriteriaView;
    this.indexSupplier = indexSupplier;
    initializeLayout();

    this.text = new Paragraph("#" + (indexSupplier.get() + 1));
    this.text.addClassNames(
        LumoUtility.TextAlignment.CENTER,
        LumoUtility.TextColor.SECONDARY,
        LumoUtility.Margin.Bottom.NONE);

    add(filterCriteriaView);
    add(createIndexColumn(onRemoveClick));
  }

  private FlexLayout createIndexColumn(Consumer<FilterCriteriaViewContainer> onRemoveClick) {
    final var vl = new FlexLayout();
    vl.addClassName(LumoUtility.Padding.Top.XSMALL);
    vl.setMinWidth("60px");
    vl.setFlexDirection(FlexLayout.FlexDirection.COLUMN);
    vl.getStyle().set("background-color", "var(--lumo-contrast-5pct)");
    vl.add(this.text);

    if (onRemoveClick != null) {
      vl.add(createRemoveButton(onRemoveClick));
    }

    return vl;
  }

  private Button createRemoveButton(Consumer<FilterCriteriaViewContainer> onRemoveClick) {
    var btn = new Button(new Icon(VaadinIcon.TRASH));
    btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    btn.addClickListener(buttonClickEvent -> onRemoveClick.accept(this));

    return btn;
  }

  @Override
  public void onFilterCriteriaCountChanged(int oldCount, int newCount) {
    text.setText("#" + (indexSupplier.get() + 1));
  }

  private void initializeLayout() {
    setWidthFull();
    setMargin(false);
    setPadding(false);
    addClassNames(LumoUtility.Border.TOP, LumoUtility.BorderColor.CONTRAST_20);
  }
}
