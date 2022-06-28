/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.registry;

import java.util.*;
import org.everit.json.schema.Schema;
import org.factcast.store.registry.transformation.Transformation;
import org.factcast.store.registry.transformation.TransformationKey;
import org.factcast.store.registry.transformation.TransformationStoreListener;
import org.factcast.store.registry.validation.schema.SchemaKey;

/**
 * used to inject instead of real registry, if validation is disabled
 *
 * @author uwe
 */
public class NOPSchemaRegistry implements SchemaRegistry {
  private static final List<Transformation> EMPTY = new LinkedList<>();

  @Override
  public Optional<Schema> get(SchemaKey key) {
    return Optional.empty();
  }

  @Override
  public List<Transformation> get(TransformationKey key) {
    return EMPTY;
  }

  @Override
  public void refresh() {
    // NOP
  }

  @Override
  public void fetchInitial() {
    // NOP
  }

  @Override
  public void register(TransformationStoreListener transformationChains) {
    // NOP
  }
}
