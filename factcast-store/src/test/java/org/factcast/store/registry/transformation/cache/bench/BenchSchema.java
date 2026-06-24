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

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

/**
 * Sets up a local benchmark playground for the transformationcache table in four flavours and loads
 * the exported CSV into them. Pure JDBC, points at a local Postgres (defaults to the one started
 * via {@code brew services start postgresql@15}); it does NOT use testcontainers.
 *
 * <p>Variants (all share jsonb header/payload, STORAGE EXTENDED and toast_tuple_target=256 so sizes
 * are comparable):
 *
 * <ul>
 *   <li>{@code tc_v1_old} — master: varchar cache_key PK (COLLATE "C") + HASH index, last_access
 *   <li>{@code tc_v2_new} — issue4589: varchar cache_key PK + fact_id uuid btree, created_at
 *   <li>{@code tc_v3_composite} — review comment: PK (fact_id, version, path int[])
 *   <li>{@code tc_v3b_composite} — same without the redundant version: PK (fact_id, path int[])
 * </ul>
 *
 * <p>Override the connection with {@code -Dpg.url=... -Dpg.user=... -Dpg.pass=...} and the CSV
 * location with {@code -Dbench.csv=/abs/path.csv}. Force a full rebuild with {@code
 * -Dbench.rebuild=true}.
 *
 * <p>Run standalone to (re)load and print row counts: {@code mvn -pl factcast-store test-compile
 * exec:java -Dexec.classpathScope=test
 * -Dexec.mainClass=org.factcast.store.registry.transformation.cache.bench.BenchSchema}
 */
public final class BenchSchema {

  static final String URL =
      System.getProperty("pg.url", "jdbc:postgresql://localhost:5432/factcast");
  static final String USER = System.getProperty("pg.user", System.getProperty("user.name"));
  static final String PASS = System.getProperty("pg.pass", "");

  static final String STAGING = "tc_staging";
  public static final String V1 = "tc_v1_old";
  public static final String V2 = "tc_v2_new";
  public static final String V3 = "tc_v3_composite";
  public static final String V3B = "tc_v3b_composite";

  /**
   * only rows whose cache_key is {uuid(36)}-{version}-[a, b, ...] are loaded, identically, in all
   */
  private static final String VALID = " WHERE cache_key ~ '^.{36}-[0-9]+-\\[[0-9, ]+\\]$'";

  private BenchSchema() {}

  public static Connection connect() throws SQLException {
    return DriverManager.getConnection(URL, USER, PASS);
  }

  /** Loads staging + builds the four variants if they are missing (or if -Dbench.rebuild=true). */
  public static void ensureReady(Connection c) throws Exception {
    boolean rebuild = Boolean.getBoolean("bench.rebuild");
    if (rebuild) {
      for (String t : new String[] {V1, V2, V3, V3B, STAGING}) exec(c, "DROP TABLE IF EXISTS " + t);
    }
    ensureStaging(c);
    if (rebuild || !exists(c, V1)) buildV1(c);
    if (rebuild || !exists(c, V2)) buildV2(c);
    if (rebuild || !exists(c, V3)) buildV3(c);
    if (rebuild || !exists(c, V3B)) buildV3b(c);
  }

  private static void ensureStaging(Connection c) throws Exception {
    if (exists(c, STAGING) && count(c, STAGING) > 0) return;
    exec(c, "DROP TABLE IF EXISTS " + STAGING);
    exec(c, "CREATE UNLOGGED TABLE " + STAGING + " (cache_key text, header jsonb, payload jsonb)");
    Path csv = locateCsv();
    System.out.println("[bench] COPY from " + csv + " ...");
    long t0 = System.currentTimeMillis();
    try (InputStream in = new BufferedInputStream(Files.newInputStream(csv))) {
      CopyManager cm = c.unwrap(PGConnection.class).getCopyAPI();
      long rows =
          cm.copyIn(
              "COPY "
                  + STAGING
                  + " (cache_key, header, payload) FROM STDIN WITH (FORMAT csv, HEADER true)",
              in);
      System.out.printf(
          "[bench] staged %,d rows in %,d ms%n", rows, System.currentTimeMillis() - t0);
    }
    exec(c, "ANALYZE " + STAGING);
  }

  private static void buildV1(Connection c) throws Exception {
    exec(c, "DROP TABLE IF EXISTS " + V1);
    exec(
        c,
        "CREATE TABLE "
            + V1
            + " (cache_key varchar(2048) COLLATE \"C\" NOT NULL,"
            + " header jsonb NOT NULL, payload jsonb NOT NULL,"
            + " last_access timestamptz NOT NULL DEFAULT now(),"
            + " CONSTRAINT pk_tc_v1 PRIMARY KEY (cache_key))");
    applyStorage(c, V1, true);
    exec(
        c,
        "INSERT INTO "
            + V1
            + " (cache_key, header, payload)"
            + " SELECT cache_key, header, payload FROM "
            + STAGING
            + VALID);
    // HASH index backed the equality lookups; PK btree (COLLATE C) backs the LIKE 'factId%' delete
    exec(c, "CREATE INDEX idx_v1_cache_key_hash ON " + V1 + " USING HASH (cache_key)");
    exec(c, "ANALYZE " + V1);
    System.out.printf("[bench] %s built: %,d rows%n", V1, count(c, V1));
  }

  private static void buildV2(Connection c) throws Exception {
    exec(c, "DROP TABLE IF EXISTS " + V2);
    exec(
        c,
        "CREATE TABLE "
            + V2
            + " (cache_key varchar(2048) COLLATE \"C\" NOT NULL, fact_id uuid NOT NULL,"
            + " header jsonb NOT NULL, payload jsonb NOT NULL,"
            + " created_at timestamptz NOT NULL DEFAULT now(),"
            + " CONSTRAINT pk_tc_v2 PRIMARY KEY (cache_key))");
    applyStorage(c, V2, true);
    exec(
        c,
        "INSERT INTO "
            + V2
            + " (cache_key, fact_id, header, payload)"
            + " SELECT cache_key, substring(cache_key,1,36)::uuid, header, payload FROM "
            + STAGING
            + VALID);
    exec(c, "CREATE INDEX idx_v2_fact_id ON " + V2 + " (fact_id)");
    exec(c, "ANALYZE " + V2);
    System.out.printf("[bench] %s built: %,d rows%n", V2, count(c, V2));
  }

  private static void buildV3(Connection c) throws Exception {
    exec(c, "DROP TABLE IF EXISTS " + V3);
    exec(
        c,
        "CREATE TABLE "
            + V3
            + " (fact_id uuid NOT NULL, version int NOT NULL, path int[] NOT NULL,"
            + " header jsonb NOT NULL, payload jsonb NOT NULL,"
            + " created_at timestamptz NOT NULL DEFAULT now(),"
            + " CONSTRAINT pk_tc_v3 PRIMARY KEY (fact_id, version, path))");
    applyStorage(c, V3, false);
    exec(
        c,
        "INSERT INTO "
            + V3
            + " (fact_id, version, path, header, payload)"
            + " SELECT substring(cache_key,1,36)::uuid,"
            + " split_part(substring(cache_key from 38),'-',1)::int,"
            + " string_to_array(substring(cache_key from '\\[(.*)\\]'), ', ')::int[],"
            + " header, payload FROM "
            + STAGING
            + VALID);
    exec(c, "ANALYZE " + V3);
    System.out.printf("[bench] %s built: %,d rows%n", V3, count(c, V3));
  }

  private static void buildV3b(Connection c) throws Exception {
    exec(c, "DROP TABLE IF EXISTS " + V3B);
    exec(
        c,
        "CREATE TABLE "
            + V3B
            + " (fact_id uuid NOT NULL, path int[] NOT NULL,"
            + " header jsonb NOT NULL, payload jsonb NOT NULL,"
            + " created_at timestamptz NOT NULL DEFAULT now(),"
            + " CONSTRAINT pk_tc_v3b PRIMARY KEY (fact_id, path))");
    applyStorage(c, V3B, false);
    exec(
        c,
        "INSERT INTO "
            + V3B
            + " (fact_id, path, header, payload)"
            + " SELECT substring(cache_key,1,36)::uuid,"
            + " string_to_array(substring(cache_key from '\\[(.*)\\]'), ', ')::int[],"
            + " header, payload FROM "
            + STAGING
            + VALID);
    exec(c, "ANALYZE " + V3B);
    System.out.printf("[bench] %s built: %,d rows%n", V3B, count(c, V3B));
  }

  /**
   * mirror issue1161/issue2131: jsonb + EXTENDED payload/header, low toast target, MAIN cache_key
   */
  private static void applyStorage(Connection c, String table, boolean hasCacheKey)
      throws Exception {
    if (hasCacheKey) exec(c, "ALTER TABLE " + table + " ALTER COLUMN cache_key SET STORAGE MAIN");
    exec(c, "ALTER TABLE " + table + " ALTER COLUMN header SET STORAGE EXTENDED");
    exec(c, "ALTER TABLE " + table + " ALTER COLUMN payload SET STORAGE EXTENDED");
    exec(c, "ALTER TABLE " + table + " SET (toast_tuple_target = 256)");
  }

  private static boolean exists(Connection c, String table) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
      ps.setString(1, table);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && rs.getBoolean(1);
      }
    }
  }

  static long count(Connection c, String table) throws SQLException {
    try (Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT count(*) FROM " + table)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static void exec(Connection c, String sql) throws SQLException {
    try (Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  private static Path locateCsv() {
    String override = System.getProperty("bench.csv");
    if (override != null) return Paths.get(override);
    for (String candidate :
        new String[] {
          "../postgres_public_transformationcache.csv",
          "postgres_public_transformationcache.csv",
          System.getProperty("user.dir") + "/../postgres_public_transformationcache.csv"
        }) {
      Path p = Paths.get(candidate);
      if (Files.isRegularFile(p)) return p.toAbsolutePath().normalize();
    }
    throw new IllegalStateException(
        "CSV not found. Pass -Dbench.csv=/abs/path/postgres_public_transformationcache.csv");
  }

  public static void main(String[] args) throws Exception {
    try (Connection c = connect()) {
      ensureReady(c);
      System.out.println("[bench] ready.");
    }
  }
}
