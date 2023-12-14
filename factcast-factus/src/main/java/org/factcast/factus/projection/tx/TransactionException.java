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
package org.factcast.factus.projection.tx;

/**
 * signals a problem with transaction state or IOExceptions or similar in communication with a
 * transactional store. Whenever this exception is thrown an underlying transaction (if exists)
 * **must be** invalidated (rolled back, set to rollback-only or similar).
 */
public class TransactionException extends Exception {
  public TransactionException(String msg) {
    super(msg);
  }

  public TransactionException(Throwable e) {
    super(e);
  }

  public TransactionException(String msg, Throwable e) {
    super(msg, e);
  }
}
