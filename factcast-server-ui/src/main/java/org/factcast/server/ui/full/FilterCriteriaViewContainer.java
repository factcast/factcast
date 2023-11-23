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

import com.google.common.collect.Lists;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;

public class FilterCriteriaViewContainer extends HorizontalLayout {
  @Getter @NonNull private final FilterCriteriaView filterCriteriaView;

  public FilterCriteriaViewContainer(@NonNull FilterCriteriaView c) {
    super(c);
    this.filterCriteriaView = c;
  }

  public FilterCriteriaViewContainer(@NonNull FilterCriteriaView c, Component... extra) {
    super(asList(c, extra).toArray(new Component[] {}));
    this.filterCriteriaView = c;
  }

  private static List<Component> asList(FilterCriteriaView c, Component... extra) {
    ArrayList<Component> components = Lists.newArrayList(extra);
    components.add(0, c);
    return components;
  }
}
