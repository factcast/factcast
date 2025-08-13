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
package org.factcast.core.store;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.spec.FactSpec;

@RequiredArgsConstructor
public abstract class AbstractFactStore implements FactStore {
  @NonNull protected final TokenStore tokenStore;

  // TODO negate
  // TODO remove stupid optional use?
  public boolean hasNoConflictingChangeUntil(
      @Nullable Long until, @NonNull Optional<StateToken> optionalToken) {

    if (optionalToken.isPresent()) {
      StateToken token = optionalToken.get();
      Optional<State> state = tokenStore.get(token);

      if (state.isPresent()) {
        try {
          return isStateUnchanged(until, state.get());
        } finally {
          invalidate(token);
        }
      } else {
        // token is unknown, just reject.
        return false;
      }
    } else {
      // publish unconditionally
      return true;
    }
  }

  @Override
  public void invalidate(@NonNull StateToken token) {
    tokenStore.invalidate(token);
  }

  @Override
  @NonNull
  public StateToken stateFor(@NonNull List<FactSpec> specs) {
    State state = getStateFor(specs, 0, null);
    return tokenStore.create(state);
  }

  @Override
  @NonNull
  public StateToken currentStateFor(@NonNull List<FactSpec> specs) {
    State state = getCurrentStateFor(specs);
    return tokenStore.create(state);
  }

  @SuppressWarnings("WeakerAccess")
  protected final boolean isStateUnchanged(@Nullable Long until, @NonNull State snapshotState) {
    long serialOfLastMatchingFact = snapshotState.serialOfLastMatchingFact();

    State currentState = getStateFor(snapshotState.specs(), serialOfLastMatchingFact, until);
    return currentState.serialOfLastMatchingFact() == 0L;
  }

  @NonNull
  protected abstract State getCurrentStateFor(List<FactSpec> specs);

  /** This can be overridden for performance optimizations */
  @NonNull
  protected abstract State getStateFor(
      @NonNull List<FactSpec> specs, long lastMatchingSerial, @Nullable Long toExclusive);
}
