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
package org.factcast.factus.projection;

/** represents the state of the projector during application of on fact or a batch of facts */
public interface ProjectorContext {

  // not sure if reusable
  LocalProjectorContext EMPTY = new LocalProjectorContext();

  static LocalProjectorContext local() {
    return EMPTY;
  }
  // TODO maybe replace by currentThread?

  // should we delegate the resolvers from here?
  //  pro: less clutter|we can loose the PC parameter on resolvers,
  //  con: PC is call scope, resolvers currently are application scope
}
