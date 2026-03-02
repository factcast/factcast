/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.grpc.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.CompressorRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompressionCodecsTest {

  @Spy private CompressorRegistry compressorRegistry = CompressorRegistry.getDefaultInstance();
  @InjectMocks private CompressionCodecs underTest;

  @Nested
  class WhenSelectingFrom {

    @BeforeEach
    void setup() {}

    @Test
    void selectsGzipIfThisIsTheOnlyAvail() {
      Assertions.assertThat(underTest.selectFrom("a  ,b , gzip, snappy ")).hasValue("gzip");
    }
  }

  @Nested
  class WhenAvailabling {
    @Test
    void avail() {
      Assertions.assertThat(underTest.available()).isEqualTo("gzip");
    }
  }
}
