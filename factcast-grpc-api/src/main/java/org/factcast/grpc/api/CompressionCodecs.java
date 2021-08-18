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
package org.factcast.grpc.api;

import io.grpc.CompressorRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.*;

public class CompressionCodecs {

  private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

  private final List<String> orderedListOfAvailableCodecs;

  private final String orderedListOfAvailableCodecsAsString;

  public CompressionCodecs() {
    orderedListOfAvailableCodecs =
        Stream.of("lz4", "snappy", "gzip")
            .filter(CompressionCodecs::locallyAvailable)
            .collect(Collectors.toList());
    orderedListOfAvailableCodecsAsString = String.join(",", orderedListOfAvailableCodecs);
  }

  private static boolean locallyAvailable(String codec) {
    return CompressorRegistry.getDefaultInstance().lookupCompressor(codec) != null;
  }

  public Optional<String> selectFrom(String commaSeparatedList) {
    return cache.computeIfAbsent(commaSeparatedList, this::fromCommaSeparatedList);
  }

  public String available() {
    return orderedListOfAvailableCodecsAsString;
  }

  private Optional<String> fromCommaSeparatedList(String listOrNull) {
    if (listOrNull != null) {
      List<String> codecs =
          Arrays.stream(listOrNull.toLowerCase().split(","))
              .map(String::trim)
              .filter(s -> !s.trim().isEmpty())
              .collect(Collectors.toList());
      for (String codec : orderedListOfAvailableCodecs) {
        if (codecs.contains(codec)) return Optional.of(codec);
      }
    }
    return Optional.empty();
  }
}
