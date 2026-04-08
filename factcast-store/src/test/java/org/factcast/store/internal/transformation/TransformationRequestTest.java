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
package org.factcast.store.internal.transformation;

import static org.assertj.core.api.Assertions.*;

import java.util.Set;
import org.factcast.core.TestFact;
import org.factcast.store.internal.PgFact;
import org.junit.jupiter.api.Test;

class TransformationRequestTest {

  @Test
  void popReturnsFactAndClearsReference() {
    PgFact fact = PgFact.from(new TestFact().version(1));
    TransformationRequest uut = new TransformationRequest(fact, Set.of(2));

    PgFact consumed = uut.pop();

    assertThat(consumed).isSameAs(fact);
    assertThat(uut.toTransform()).isNull();
  }

  @Test
  void consumeToTransformThrowsOnDoubleConsume() {
    PgFact fact = PgFact.from(new TestFact().version(1));
    TransformationRequest uut = new TransformationRequest(fact, Set.of(2));

    uut.pop();

    assertThatThrownBy(uut::pop)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("PgFact already consumed");
  }
}
