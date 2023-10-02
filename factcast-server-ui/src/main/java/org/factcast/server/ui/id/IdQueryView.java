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

import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

public class IdQueryView extends HorizontalLayout {
  //
  //  private final IdQueryBean formBean;
  //  private TextField name;
  //  private Button sayHello;
  //
  //  TextField id = new TextField("id");
  //
  //  public IdQueryView() {
  //
  //    this.formBean = new IdQueryBean();
  //
  //    Binder<IdQueryBean> b = new BeanValidationBinder<>(IdQueryBean.class);
  //    b.forField(id)
  //        .withNullRepresentation("")
  //        .withConverter(new StringToUuidConverter("TODO not a uuid"))
  //        .bind("id");
  //    b.readBean(formBean);
  //
  //    Button query = new Button("query");
  //    query.addClickListener(
  //        event -> {
  //          try {
  //            b.writeBean(formBean);
  //          } catch (ValidationException e) {
  //            throw new RuntimeException(e);
  //          }
  //          System.out.println("foo " + formBean);
  //        });
  //
  //    add(id, query);
  //  }
}
