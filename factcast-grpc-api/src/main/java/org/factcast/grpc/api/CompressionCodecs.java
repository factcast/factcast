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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CompressionCodecs {

  private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

  private final List<String> orderedListOfAvailableCodecs;

  private final String orderedListOfAvailableCodecsAsString;

  private final CompressorRegistry compressorRegistry;

  // lz4c/snappyc originate from commons.compress, see dedicated modules.
  private static final String[] AVAIL_CODECS = {"lz4", "snappyc", "snappy", "gzip"};

  public CompressionCodecs(CompressorRegistry compressorRegistry) {
    this.compressorRegistry = compressorRegistry;
    orderedListOfAvailableCodecs =
        Stream.of(AVAIL_CODECS).filter(this::locallyAvailable).collect(Collectors.toList());
    orderedListOfAvailableCodecsAsString = String.join(",", orderedListOfAvailableCodecs);
  }

  private boolean locallyAvailable(String codec) {
    return compressorRegistry.lookupCompressor(codec) != null;
  }

  public Optional<String> selectFrom(String commaSeparatedList) {
    if (commaSeparatedList == null) {
      return Optional.empty();
    } else {
      return cache.computeIfAbsent(commaSeparatedList, this::fromCommaSeparatedList);
    }
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
        if (codecs.contains(codec)) {
          return Optional.of(codec);
        }
      }
    }
    return Optional.empty();
  }
}
