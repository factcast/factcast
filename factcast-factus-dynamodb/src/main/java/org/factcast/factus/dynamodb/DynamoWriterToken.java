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

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.google.common.annotations.VisibleForTesting;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import org.factcast.factus.projection.WriterToken;

public class DynamoWriterToken implements WriterToken {

  private final DynamoOperations dynamo;
  private final String lockIdentifier;
  private final Timer timer;
  private final AtomicBoolean liveness;

  @VisibleForTesting
  protected DynamoWriterToken(
      @NonNull AmazonDynamoDBClient client, String projectionIdentifier, @NonNull Timer timer) {
    this.dynamo = new DynamoOperations(client);
    this.lockIdentifier = projectionIdentifier;
    this.timer = timer;
    liveness = new AtomicBoolean(dynamo.retrieveLockState(lockIdentifier));
    long watchDogTimeout = 10000; // 10 seconds
    TimerTask timerTask =
        new TimerTask() {
          @Override
          public void run() {
            if (liveness.get()) dynamo.lock(lockIdentifier);
          }
        };
    timer.scheduleAtFixedRate(timerTask, 0, (long) (watchDogTimeout / 1.5));
  }

  protected DynamoWriterToken(@NonNull AmazonDynamoDBClient client, String projectionIdentifier) {
    this(client, projectionIdentifier, new Timer());
  }

  @Override
  public void close() throws Exception {
    liveness.set(false);
    timer.cancel();
    dynamo.removeLock(lockIdentifier);
  }

  @Override
  public boolean isValid() {
    return liveness.get();
  }
}
