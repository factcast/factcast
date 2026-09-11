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
package org.factcast.factus;

import java.lang.annotation.*;

/**
 * Applies the annotated handler only to those Facts that carry the Aggregate's own id in the given
 * property, which is useful for events referencing an Aggregate in more than one role (think
 * 'userId' and 'deletedByUserId').
 *
 * <p>Only valid on handlers of {@link org.factcast.factus.projection.Aggregate}s, as the id to
 * compare to is the Aggregate's own. The handler needs to declare exactly one {@link
 * org.factcast.factus.event.EventObject} parameter: the property path is resolved against that
 * event class, using its <b>fields</b> rather than its getters. It can therefore not be combined
 * with {@link HandlerFor}, which has no event class to resolve against; doing so logs an error and
 * results in no filtering at all.
 *
 * <p>Note that, unlike the other filter annotations, this one is not part of the FactSpec and hence
 * evaluated client-side: non-matching Facts are still received, just not handed to the handler.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FilterByAggIdProperty {

  /** the path to the UUID property to compare to the Aggregate's id, like 'references.userId' */
  String value();
}
