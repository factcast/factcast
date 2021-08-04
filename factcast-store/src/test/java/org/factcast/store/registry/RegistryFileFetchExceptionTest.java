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

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import org.junit.jupiter.api.*;

public class RegistryFileFetchExceptionTest {
  @Test
  void testNullContracts() throws Exception {
    assertThrows(NullPointerException.class, () -> new RegistryFileFetchException(null, 7, ""));
    assertThrows(
        NullPointerException.class,
        () -> new RegistryFileFetchException(new URL("http://ibm.com"), 7, null));

    new RegistryFileFetchException(new URL("http://ibm.com"), 7, "must not throw exception");
  }
}
