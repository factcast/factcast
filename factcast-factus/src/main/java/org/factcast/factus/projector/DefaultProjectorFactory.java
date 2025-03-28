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
package org.factcast.factus.projector;

import lombok.RequiredArgsConstructor;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projection.parameter.HandlerParameterContributors;

@RequiredArgsConstructor
public class DefaultProjectorFactory implements ProjectorFactory {

  final EventSerializer serializer;
  final HandlerParameterContributors contributors;

  @Override
  public <A extends Projection> Projector<A> create(A projection) {
    return new ProjectorImpl<>(projection, serializer, contributors);
  }
}
