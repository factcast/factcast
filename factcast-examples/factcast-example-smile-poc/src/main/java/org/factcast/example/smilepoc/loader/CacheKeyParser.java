/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.example.smilepoc.loader;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CacheKeyParser {

  private static final Pattern PATTERN = Pattern.compile("^([0-9a-fA-F-]{36})-(\\d+)-(\\[.*\\])$");

  public record Parsed(UUID factId, int version, String chainId) {}

  public static Parsed parse(String cacheKey) {
    Matcher m = PATTERN.matcher(cacheKey);
    if (!m.matches()) {
      throw new IllegalArgumentException("cache_key does not match expected shape: " + cacheKey);
    }
    return new Parsed(UUID.fromString(m.group(1)), Integer.parseInt(m.group(2)), m.group(3));
  }

  private CacheKeyParser() {}
}
