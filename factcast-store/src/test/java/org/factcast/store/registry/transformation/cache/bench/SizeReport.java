/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.store.registry.transformation.cache.bench;

import java.sql.*;

/**
 * Prints heap / TOAST / index / total sizes for each variant so we can settle the "does the
 * composite key actually shrink anything?" question from the review comment.
 *
 * <p>Run: {@code mvn -pl factcast-store test-compile exec:java -Dexec.classpathScope=test
 * -Dexec.mainClass=org.factcast.store.registry.transformation.cache.bench.SizeReport}
 */
public final class SizeReport {

  private SizeReport() {}

  public static void main(String[] args) throws Exception {
    try (Connection c = BenchSchema.connect()) {
      BenchSchema.ensureReady(c);

      System.out.printf(
          "%n%-18s %10s %12s %10s %12s %12s%n",
          "table", "rows", "heap", "toast", "indexes", "total");
      System.out.println("-".repeat(78));
      for (String t :
          new String[] {BenchSchema.V1, BenchSchema.V2, BenchSchema.V3, BenchSchema.V3B}) {
        printTable(c, t);
      }

      System.out.printf("%n%-40s %12s%n", "index", "size");
      System.out.println("-".repeat(54));
      for (String t :
          new String[] {BenchSchema.V1, BenchSchema.V2, BenchSchema.V3, BenchSchema.V3B}) {
        printIndexes(c, t);
      }
    }
  }

  private static void printTable(Connection c, String table) throws SQLException {
    String sql =
        "SELECT pg_relation_size(c.oid),"
            + " COALESCE(pg_relation_size(c.reltoastrelid),0),"
            + " pg_indexes_size(c.oid),"
            + " pg_total_relation_size(c.oid)"
            + " FROM pg_class c WHERE c.oid = ?::regclass";
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, table);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        System.out.printf(
            "%-18s %,10d %12s %10s %12s %12s%n",
            table,
            BenchSchema.count(c, table),
            human(rs.getLong(1)),
            human(rs.getLong(2)),
            human(rs.getLong(3)),
            human(rs.getLong(4)));
      }
    }
  }

  private static void printIndexes(Connection c, String table) throws SQLException {
    String sql =
        "SELECT indexrelid::regclass::text, pg_relation_size(indexrelid)"
            + " FROM pg_stat_user_indexes WHERE relname = ? ORDER BY 1";
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, table);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          System.out.printf("%-40s %12s%n", rs.getString(1), human(rs.getLong(2)));
        }
      }
    }
  }

  private static String human(long bytes) {
    if (bytes < 1024) return bytes + " B";
    double kb = bytes / 1024.0;
    if (kb < 1024) return String.format("%.1f kB", kb);
    return String.format("%.1f MB", kb / 1024.0);
  }
}
