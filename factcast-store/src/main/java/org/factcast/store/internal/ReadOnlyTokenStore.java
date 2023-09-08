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
package org.factcast.store.internal;

import java.util.Optional;
import lombok.NonNull;
import org.factcast.core.store.State;
import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;

public class ReadOnlyTokenStore implements TokenStore {
  @Override
  public @NonNull StateToken create(@NonNull State state) {
    throw new UnsupportedOperationException("Creating tokens is not allowed in read-only mode");
  }

  @Override
  public void invalidate(@NonNull StateToken token) {
    throw new UnsupportedOperationException("Invalidating tokens is not allowed in read-only mode");
  }

  @Override
  public @NonNull Optional<State> get(@NonNull StateToken token) {
    throw new UnsupportedOperationException("tokens are not available in read-only mode");
  }
}
