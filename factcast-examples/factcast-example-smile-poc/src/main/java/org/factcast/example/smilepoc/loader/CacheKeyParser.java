package org.factcast.example.smilepoc.loader;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CacheKeyParser {

  private static final Pattern PATTERN =
      Pattern.compile("^([0-9a-fA-F-]{36})-(\\d+)-(\\[.*\\])$");

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
