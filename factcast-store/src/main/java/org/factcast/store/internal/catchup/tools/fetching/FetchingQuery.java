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
package org.factcast.store.internal.catchup.tools.fetching;

import java.sql.*;
import lombok.*;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.catchup.RowProcessor;

public interface FetchingQuery {
  static FetchingQuery create(@NonNull StoreConfigurationProperties props) {
    return props.isCatchupAsyncFetch() ? FetchingQuery.async() : FetchingQuery.sync();
  }

  void executeAndProcess(
      @NonNull PreparedStatement ps,
      @NonNull RowProcessor rowProcessor,
      @NonNull Runnable callbackBeforeProcessing)
      throws SQLException;

  static FetchingQuery sync() {
    return new DefaultFetchingQuery();
  }

  static FetchingQuery async() {
    return new PreFetchingQuery();
  }
}
