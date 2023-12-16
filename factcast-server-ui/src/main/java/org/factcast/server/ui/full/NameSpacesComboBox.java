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

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.data.provider.DataProvider;
import java.util.Collection;
import org.factcast.core.util.NoCoverageReportToBeGenerated;

@NoCoverageReportToBeGenerated
class NameSpacesComboBox extends ComboBox<String> {
  public NameSpacesComboBox(Collection<String> items) {
    super("Namespace");
    setItems(DataProvider.ofCollection(items));
    setAutoOpen(true);
    setAutofocus(true);
    getStyle().set("--vaadin-combo-box-overlay-width", "16em");
  }
}