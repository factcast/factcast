/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.factus.projection;

import java.lang.annotation.*;
import org.factcast.factus.event.EventObject;

/** using default type is only allowed on Method */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Repeatable(OverrideNamespaces.class)
public @interface OverrideNamespace {
  Class<? extends EventObject> DISCOVER = DiscoverFromMethodParameter.class;

  /** the namespace to overrule the @specification ns from the type. */
  String ns();

  /** The type on which to overrule the ns */
  Class<? extends EventObject> type() default DiscoverFromMethodParameter.class;

  abstract class DiscoverFromMethodParameter implements EventObject {}
}
