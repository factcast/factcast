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
import org.factcast.store.registry.transformation.*;
import org.factcast.store.registry.validation.schema.SchemaKey;

public interface SchemaRegistry {

  String LOCK_NAME = "schemareg_update";

  Optional<Schema> get(SchemaKey key);

  List<Transformation> get(TransformationKey key);

  void fetchInitial();

  void refresh();

  void register(TransformationStoreListener listener);

  void invalidateNearCache(SchemaKey key);

  void clearNearCache();

  Set<String> enumerateNamespaces();

  Set<String> enumerateTypes(String n);

  Set<Integer> enumerateVersions(String ns, String type);

  default boolean isActive() {
    // only false for NOP, which is used when SR is not configured.
    // shortcut to avoid using StoreConfigurationProperties.isSchemaRegistryConfigured()
    return true;
  }
}
