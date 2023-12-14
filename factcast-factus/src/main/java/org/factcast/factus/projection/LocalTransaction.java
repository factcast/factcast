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
package org.factcast.factus.projection;

import org.factcast.factus.projection.tx.TransactionAware;
import org.factcast.factus.projection.tx.TransactionException;

public class LocalTransaction implements TransactionAware<LocalProjectorContext> {

  @Override
  public LocalProjectorContext begin() throws TransactionException {
    return ProjectorContext.local();
  }

  @Override
  public void commit(LocalProjectorContext localProjectorContext) throws TransactionException {
    // nothing to do
  }

  @Override
  public void rollback(LocalProjectorContext localProjectorContext) throws TransactionException {
    throw new UnsupportedOperationException("Cannot rollback from LocalTransaction");
  }
}
