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
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;

public class FullQueryView extends FormLayout {

  private final FullQueryBean formBean;
  private TextField name;
  private Button sayHello;

  TextField ns = new TextField("ns");
  TextField type = new TextField("type");
  // TextField agg = new TextField("agg");

  public FullQueryView() {

    this.formBean = new FullQueryBean();

    setResponsiveSteps(new FormLayout.ResponsiveStep("150em", 2));

    Binder<FullQueryBean> b = new BeanValidationBinder<>(FullQueryBean.class);
    b.forField(ns).bind("ns");
    b.forField(type).bind("type");
    b.readBean(formBean);

    Button query = new Button("query");
    query.addClickListener(
        event -> {
          try {
            b.writeBean(formBean);
          } catch (ValidationException e) {
            throw new RuntimeException(e);
          }
          System.out.println("foo " + formBean);
        });

    add(ns, type, query);
  }
}
