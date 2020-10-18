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

import java.util.List;
import java.util.function.Function;
import org.factcast.core.Fact;
import org.factcast.factus.event.EventObject;

/**
 * a Batch that can be passed around and defers publishing until either execute or close is called.
 * Note that a batch can be marked to be aborted.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface PublishBatch extends AutoCloseable {
  PublishBatch add(EventObject p);

  PublishBatch add(Fact f);

  void execute() throws BatchAbortedException;

  <R> R execute(Function<List<Fact>, R> resultFunction) throws BatchAbortedException;

  PublishBatch markAborted(String msg);

  PublishBatch markAborted(Throwable cause);

  void close(); // checks if either aborted or executed already, otherwise
  // will execute
}
