/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.listen;

import com.google.common.annotations.VisibleForTesting;
import java.sql.Connection;
import java.util.*;
import lombok.*;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public class ModifiedSingleConnectionDataSource extends SingleConnectionDataSource {
  private final Connection connection;
  @VisibleForTesting @Getter private final List<ConnectionModifier> modifiers;

  public ModifiedSingleConnectionDataSource(
      @NonNull Connection connection, @NonNull List<ConnectionModifier> modifiers) {
    super(connection, true);
    this.connection = connection;
    this.modifiers = List.copyOf(modifiers);

    this.modifiers.forEach(modifier -> modifier.afterBorrow(connection));
  }

  @Override
  public void destroy() {
    var reversed = new ArrayList<>(modifiers);
    Collections.reverse(reversed);
    reversed.forEach(modifier -> modifier.beforeReturn(connection));
    super.destroy();
  }
}
