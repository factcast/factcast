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
package org.factcast.factus.projection;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ManagedProjection implements Projection, StateAware, WriterTokenAware {

  public final void withLock(Runnable runnable) {
    try {
      try (AutoCloseable token = acquireWriteToken()) {
        if (token == null) {
          throw new IllegalStateException("cannot acquire write token");
        } else {
          runnable.run();
        }
      }
    } catch (RuntimeException e) {
      // assuming coming from runnable.run()
      log.warn("While executing with lock:", e);
    } catch (Exception e) {
      // assuming coming from AutoCloseable.close()
      log.warn("While trying to release write token:", e);
    }
  }
}
