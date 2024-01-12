/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.itests.exception.handling;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.HandlerFor;
import org.factcast.factus.projection.SubscribedProjection;
import org.factcast.factus.projection.WriterToken;

@RequiredArgsConstructor
public class SubscribedUserNames implements SubscribedProjection {
  private final CountDownLatch catchupLatch;
  private final CountDownLatch errorLatch;

  @Getter private Throwable exception;

  private FactStreamPosition factStreamPosition = null;

  @HandlerFor(ns = "users", type = "UserCreated", version = 2)
  void apply(Fact f) {}

  @Override
  public void onError(@NonNull Throwable exception) {
    this.exception = exception;
    errorLatch.countDown();
    SubscribedProjection.super.onError(exception);
  }

  @Override
  public void onCatchup() {
    catchupLatch.countDown();
    SubscribedProjection.super.onCatchup();
  }

  @Override
  public FactStreamPosition factStreamPosition() {
    return factStreamPosition;
  }

  @Override
  public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {
    this.factStreamPosition = factStreamPosition;
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return () -> {};
  }
}
