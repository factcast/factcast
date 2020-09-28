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
package org.factcast.factus.batch;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.event.EventConverter;
import org.factcast.factus.event.EventObject;

@RequiredArgsConstructor
public class DefaultPublishBatch implements PublishBatch {

  private final FactCast fc;

  private final EventConverter converter;

  private final List<Supplier<Fact>> toPublish = Collections.synchronizedList(new LinkedList<>());

  private final AtomicBoolean executed = new AtomicBoolean();

  private BatchAbortedException abortedException;

  @Override
  public PublishBatch add(EventObject p) {
    toPublish.add(() -> converter.toFact(p));
    return this;
  }

  @Override
  public PublishBatch add(Fact f) {
    toPublish.add(() -> f);
    return this;
  }

  @Override
  public void execute() throws BatchAbortedException {
    execute(f -> null);
  }

  @Override
  public <R> R execute(Function<List<Fact>, R> resultFunction) throws BatchAbortedException {

    if (executed.getAndSet(true)) {
      throw new IllegalStateException("Has already been executed");
    }

    if (abortedException != null) {
      toPublish.clear();
      throw abortedException;
    } else {
      List<Fact> facts = toPublish.stream().map(Supplier::get).collect(Collectors.toList());
      fc.publish(facts);
      return resultFunction.apply(facts);
    }
  }

  @Override
  public PublishBatch markAborted(String msg) {
    this.abortedException = new BatchAbortedException(msg);
    return this;
  }

  @Override
  public PublishBatch markAborted(Throwable e) {
    this.abortedException = BatchAbortedException.wrap(e);
    return this;
  }

  @Override
  // convenience for use in a try with resources
  public void close() {
    if (!executed.get()) {
      execute();
    }
  }
}
