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
package org.factcast.factus.dynamodb;

import com.google.common.annotations.VisibleForTesting;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import org.factcast.factus.projection.WriterToken;
// TODO

public class DynamoDBWriterToken implements WriterToken {

  private final DynamoDBOperations operations;
  private final String lockId;
  private final Timer timer;
  private final AtomicBoolean valid = new AtomicBoolean(true);

  @VisibleForTesting
  protected DynamoDBWriterToken(
      @NonNull DynamoDBOperations ops, String lockId, @NonNull Timer timer) {
    this.operations = ops;
    this.lockId = lockId;
    this.timer = timer;

    TimerTask timerTask =
        new TimerTask() {
          @Override
          public void run() {
            operations.refresh(lockId);
          }
        };
    timer.scheduleAtFixedRate(timerTask, 0, DynamoDBConstants.DEFAULT_TIMEOUT);
  }

  protected DynamoDBWriterToken(@NonNull DynamoDBOperations ops, String lockId) {
    this(ops, lockId, new Timer());
  }

  @Override
  public void close() throws Exception {
    valid.set(false);
    timer.cancel();
    operations.unLock(lockId);
  }

  @Override
  public boolean isValid() {
    // necessary to recheck?
    return valid.get();
  }

  // only useful for testing
  @VisibleForTesting
  protected void kill() {
    timer.cancel();
  }
}
