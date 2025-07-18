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
package org.factcast.server.ui.id;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;
import lombok.experimental.Accessors;
import org.factcast.core.util.NoCoverageReportToBeGenerated;

@Data
@Accessors(chain = false, fluent = false)
@NoCoverageReportToBeGenerated("backing bean")
public class IdQueryBean implements Serializable {
  @NotNull private UUID id;

  private int version;
}
