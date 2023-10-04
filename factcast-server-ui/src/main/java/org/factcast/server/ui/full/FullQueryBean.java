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
package org.factcast.server.ui.full;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = false, chain = false)
public class FullQueryBean {
  private LocalDate since = LocalDate.now();
  private Integer limit = 50;
  private Integer offset = 0;
  private Set<String> ns = null;
  private Set<String> type = null;
  private Set<AggregateId> agg = new HashSet<>();
  private Set<MetaTuple> meta = new HashSet<>();
}
