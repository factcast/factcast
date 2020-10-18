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
package org.factcast.core;

import java.util.LinkedList;
import java.util.List;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FactValidation {

  private boolean lacksRequiredNamespace(Fact f) {
    return f.ns() == null || f.ns().trim().isEmpty();
  }

  private boolean lacksRequiredId(Fact f) {
    return f.id() == null;
  }

  private boolean lacksRequiredType(Fact f) {
    return f.type() == null;
  }

  public void validateOnPublish(@NonNull List<? extends Fact> facts) {
    List<String> errors = new LinkedList<>();
    facts.forEach(
        f -> {
          if (lacksRequiredNamespace(f))
            errors.add("Fact " + f.id() + " lacks required namespace.");
          if (lacksRequiredId(f)) errors.add("Fact " + f.jsonHeader() + " lacks required id.");
          if (lacksRequiredType(f)) errors.add("Fact " + f.jsonHeader() + " lacks required type.");
        });
    if (!errors.isEmpty()) throw new FactValidationException(errors);
  }
}
