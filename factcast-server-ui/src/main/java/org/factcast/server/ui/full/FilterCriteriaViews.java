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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class FilterCriteriaViews extends VerticalLayout {
  public void clear() {

    getChildren().forEach(Component::removeFromParent);
  }

  public void updateState() {
    getChildren()
        .filter(c -> c instanceof FilterCriteriaView)
        .map(f -> (FilterCriteriaView) f)
        .forEach(FilterCriteriaView::updateState);
  }
}
