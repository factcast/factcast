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

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.util.NoCoverageReportToBeGenerated;

@Getter
@NoCoverageReportToBeGenerated
public class FilterCriteriaViewContainer extends VerticalLayout {
  @NonNull private final FilterCriteriaView filterCriteriaView;

  public FilterCriteriaViewContainer(@NonNull FilterCriteriaView c) {
    super(c);
    this.filterCriteriaView = c;
    initializeLayout();
  }

  private void initializeLayout() {
    setWidthFull();
    setSpacing(false);
    setMargin(false);
    setPadding(false);
    addClassNames(
        LumoUtility.Border.BOTTOM,
        LumoUtility.BorderColor.CONTRAST_20,
        LumoUtility.Padding.Bottom.MEDIUM);
  }

  public FilterCriteriaViewContainer(@NonNull FilterCriteriaView c, RemoveButton extra) {
    super(extra, c);
    this.filterCriteriaView = c;
    initializeLayout();
  }
}
