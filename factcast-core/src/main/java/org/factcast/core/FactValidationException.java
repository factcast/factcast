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

import java.io.Serial;
import java.util.List;

public class FactValidationException extends IllegalArgumentException {

  @Serial private static final long serialVersionUID = 1L;

  public FactValidationException(String msg) {
    super(msg);
  }

  public FactValidationException(List<String> errors) {
    super(render(errors));
  }

  private static String render(List<String> errors) {
    return "\n" + String.join("\n", errors);
  }
}
