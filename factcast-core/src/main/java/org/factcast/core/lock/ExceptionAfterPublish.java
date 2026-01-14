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
package org.factcast.core.lock;

import java.io.Serial;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;

public final class ExceptionAfterPublish extends IllegalStateException {

  @Serial private static final long serialVersionUID = 1L;

  @Getter @NonNull private final List<Fact> publishedFacts;

  public ExceptionAfterPublish(@NonNull List<Fact> publishedFacts, @NonNull Throwable e) {
    super(
        "An exception has happened in the 'andThen' part of your publishing attempt. This is a"
            + " programming error, as the runnable in andThen is not supposed to throw an"
            + " Exception. Note that publish actually worked, and the ids of your the published"
            + " facts are "
            + render(publishedFacts),
        e);
    this.publishedFacts = publishedFacts;
  }

  private static String render(@NonNull List<Fact> publishedFacts) {
    return FactCastJson.writeValueAsString(
        publishedFacts.stream().map(Fact::id).collect(Collectors.toList()));
  }
}
