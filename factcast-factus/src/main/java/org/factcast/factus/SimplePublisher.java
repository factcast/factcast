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

import java.util.*;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.factus.event.EventObject;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public interface SimplePublisher {
  /** publishes a single event immediately */
  void publish(@NonNull EventObject eventPojo);

  /** publishes a list of events immediately in an atomic manner (all or none) */
  void publish(@NonNull List<EventObject> eventPojos);

  /** In case you'd need to assemble a fact yourself */
  void publish(@NonNull Fact f);
}
