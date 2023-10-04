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
package org.factcast.server.ui.id;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.views.MainLayout;

@Route(value = "ui/id", layout = MainLayout.class)
@PageTitle("by Id")
@AnonymousAllowed
public class IdQueryPage extends VerticalLayout {
  public IdQueryPage(FactRepository fr) {
    IdQueryView query = new IdQueryView(fr);
    // JsonDisplayView display = new JsonDisplayView();
    add(query);
    setWidthFull();
  }
}
