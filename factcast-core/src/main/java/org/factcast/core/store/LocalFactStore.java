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
package org.factcast.core.store;

import java.time.LocalDate;
import java.util.Optional;
import lombok.NonNull;
import org.factcast.core.Fact;

public interface LocalFactStore extends FactStore {

  /**
   * @return 0 if the store is empty
   */
  long latestSerial();

  /**
   * @since 0.7.1
   * @return 0 if the store is empty
   */
  long lastSerialBefore(@NonNull LocalDate date);

  /**
   * @since 0.7.1
   * @param serial to look for
   * @return the Fact stored with that serial or empty if it does not exist
   */
  Optional<Fact> fetchBySerial(long serial);
}
