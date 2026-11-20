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
package org.factcast.factus.jdbc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Derives the row keys used in the lock table and in the fact-stream-position tables from a
 * projection's scoped name.
 */
@UtilityClass
public class ProjectionNames {

  /** Width of the {@code name} column in both the lock table and the position tables. */
  public final int MAX_NAME_LENGTH = 255;

  public final String LOCK_SUFFIX = "_lock";

  private final String SEPARATOR = "_";
  private final int HASH_LENGTH = 8;

  public String lockName(@NonNull String scopedName) {
    return shorten(scopedName, LOCK_SUFFIX);
  }

  public String positionName(@NonNull String scopedName) {
    return shorten(scopedName);
  }

  public String shorten(@NonNull String name) {
    return shorten(name, "");
  }

  /**
   * Must not change once released: renaming locks during a rolling deploy would let old and new
   * instances hold two different locks over the same projection.
   */
  private String shorten(@NonNull String name, @NonNull String suffix) {
    String full = name + suffix;
    if (full.length() <= MAX_NAME_LENGTH) {
      return full;
    }
    int retained = MAX_NAME_LENGTH - suffix.length() - HASH_LENGTH - SEPARATOR.length();
    return name.substring(0, retained) + SEPARATOR + sha256Prefix(full) + suffix;
  }

  private String sha256Prefix(String name) {
    byte[] digest = sha256().digest(name.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(HASH_LENGTH);
    for (int i = 0; i < HASH_LENGTH / 2; i++) {
      hex.append(String.format("%02x", digest[i]));
    }
    return hex.toString();
  }

  private MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
