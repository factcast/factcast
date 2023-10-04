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
import java.util.Collection;
import org.vaadin.crudui.crud.CrudListener;
import org.vaadin.crudui.crud.impl.GridCrud;

class AggregateIdView extends VerticalLayout {
  AggregateIdView(FullQueryBean backingBean) {

    GridCrud<AggregateId> crud = new GridCrud<>(AggregateId.class);

    crud.setCrudListener(
        new CrudListener<AggregateId>() {
          @Override
          public Collection<AggregateId> findAll() {
            return backingBean.getAgg();
          }

          @Override
          public AggregateId add(AggregateId agg) {
            // should not be directly pushed into the formbean, TODO learn about grids and
            // binding
            backingBean.getAgg().add(agg);
            return agg;
          }

          @Override
          public AggregateId update(AggregateId agg) {
            return agg;
          }

          @Override
          public void delete(AggregateId agg) {
            // should not be directly removed from the formbean, TODO learn about grids and
            // binding
            backingBean.getAgg().remove(agg);
          }
        });
    crud.getCrudFormFactory().setUseBeanValidation(true);
    setId("aggbox");
    add(crud);
    setWidthFull();
    crud.setWidthFull();
  }
}
