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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedList;
import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.junit.jupiter.api.*;

public class AttemptTest {

  @Test
  public void testAbort() {
    assertThrows(AttemptAbortedException.class, () -> Attempt.abort("foo"));
    assertThrows(NullPointerException.class, () -> Attempt.abort(null));
  }

  @Test
  public void testPublishNPE() {
    assertThrows(NullPointerException.class, () -> Attempt.publish(null));
    assertThrows(NullPointerException.class, () -> Attempt.publish((Fact) null));
  }

  @Test
  public void testPublish() {
    assertThat( Attempt.publish(new LinkedList<>()).factsToPublish()).isEmpty();
  }

  @Test
  public void testPublishFactFactArray() {
    Fact f1 = new TestFact();
    Fact f2 = new TestFact();
    Fact f3 = new TestFact();
    IntermediatePublishResult r = Attempt.publish(f1, f2, f3);
    assertThat(r.factsToPublish().size()).isEqualTo(3);
  }

  @Test
  public void testPublishFact() {
    Fact f1 = new TestFact();
    IntermediatePublishResult r = Attempt.publish(f1);
    assertThat(r.factsToPublish().size()).isEqualTo(1);
    assertThat(r.factsToPublish().get(0)).isSameAs(f1);
  }
}
