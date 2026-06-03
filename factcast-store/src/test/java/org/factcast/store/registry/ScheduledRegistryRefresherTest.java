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
package org.factcast.store.registry;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledRegistryRefresherTest {

  @Mock private SchemaRegistry registry;

  @InjectMocks private ScheduledRegistryRefresher underTest;

  @Nested
  class WhenRefreshing {
    @BeforeEach
    void setup() {}

    @Test
    void testRefreshIsPassedThrough() {
      underTest.refresh();
      verify(registry).refresh();
    }
  }
}
