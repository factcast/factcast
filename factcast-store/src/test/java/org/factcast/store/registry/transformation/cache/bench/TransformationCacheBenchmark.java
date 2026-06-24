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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Head-to-head of the transformationcache key designs for the access patterns in {@code
 * PgTransformationCache}: point/batch lookup (find/findAll), the ON CONFLICT DO NOTHING insert, and
 * the two invalidations (by ns+type, by fact_id).
 *
 * <p>V1/V2 lookups use the production-identical {@code WHERE cache_key = ANY(?::varchar[])}. The
 * composite variants cannot do that for a ragged set of int[] paths, so they use the most
 * statement-cache-friendly shape available — {@code unnest(?::uuid[], ?::int[], ?::text[])} joined
 * on the key. The gap between the two at batchSize>1 is exactly the lookup-ergonomics cost
 * discussed in review.
 *
 * <p>Needs a populated local DB (see {@link BenchSchema}). Run via the existing {@code JMH} main,
 * e.g.: {@code java -cp <test-cp> JMH TransformationCacheBenchmark -p batchSize=1,100,500}
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@Threads(1)
public class TransformationCacheBenchmark {

  @Param({"1", "100", "500"})
  int batchSize;

  private static final int POOL = 2000;

  private Connection conn;
  private Sample[] pool;
  private final AtomicInteger cursor = new AtomicInteger();

  /** one cached row, with everything pre-parsed so the benchmark body only builds JDBC params */
  private static final class Sample {
    String cacheKey;
    String factId; // uuid as text
    int version;
    String pathArrayLiteral; // {4,3,2,1}
    String ns;
    String type;
    String header; // json text
    String payload; // json text
  }

  @Setup(Level.Trial)
  public void setup() throws Exception {
    conn = BenchSchema.connect();
    conn.setAutoCommit(true);
    BenchSchema.ensureReady(conn);
    loadPool();
  }

  @TearDown(Level.Trial)
  public void tearDown() throws Exception {
    if (conn != null) conn.close();
  }

  private void loadPool() throws SQLException {
    List<Sample> list = new ArrayList<>(POOL);
    String sql =
        "SELECT cache_key, header::text, payload::text, header->>'ns', header->>'type'"
            + " FROM "
            + BenchSchema.V2
            + " ORDER BY random() LIMIT "
            + POOL;
    try (Statement s = conn.createStatement();
        ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) {
        Sample sm = new Sample();
        sm.cacheKey = rs.getString(1);
        sm.header = rs.getString(2);
        sm.payload = rs.getString(3);
        sm.ns = rs.getString(4);
        sm.type = rs.getString(5);
        // cache_key = {uuid(36)}-{version}-[a, b, ...]
        sm.factId = sm.cacheKey.substring(0, 36);
        String rest = sm.cacheKey.substring(37);
        int dash = rest.indexOf('-');
        sm.version = Integer.parseInt(rest.substring(0, dash));
        String inside = rest.substring(dash + 1).replaceAll("[\\[\\] ]", ""); // 4,3,2,1
        sm.pathArrayLiteral = "{" + inside + "}";
        list.add(sm);
      }
    }
    pool = list.toArray(new Sample[0]);
    if (pool.length == 0) throw new IllegalStateException("empty pool - is the data loaded?");
  }

  private Sample[] nextBatch() {
    Sample[] b = new Sample[batchSize];
    int start = Math.floorMod(cursor.getAndAdd(batchSize), pool.length);
    for (int i = 0; i < batchSize; i++) b[i] = pool[(start + i) % pool.length];
    return b;
  }

  // ---------- find / findAll : WHERE cache_key = ANY(?) ----------

  @Benchmark
  public void find_v1(Blackhole bh) throws SQLException {
    findByCacheKey(BenchSchema.V1, bh);
  }

  @Benchmark
  public void find_v2(Blackhole bh) throws SQLException {
    findByCacheKey(BenchSchema.V2, bh);
  }

  private void findByCacheKey(String table, Blackhole bh) throws SQLException {
    Sample[] b = nextBatch();
    String[] keys = new String[b.length];
    for (int i = 0; i < b.length; i++) keys[i] = b[i].cacheKey;
    try (PreparedStatement ps =
        conn.prepareStatement(
            "SELECT header, payload FROM " + table + " WHERE cache_key = ANY (?)")) {
      ps.setArray(1, conn.createArrayOf("varchar", keys));
      drain(ps, bh);
    }
  }

  // ---------- find / findAll : composite key via unnest ----------

  @Benchmark
  public void find_v3(Blackhole bh) throws SQLException {
    Sample[] b = nextBatch();
    String[] ids = new String[b.length];
    Integer[] versions = new Integer[b.length];
    String[] paths = new String[b.length];
    for (int i = 0; i < b.length; i++) {
      ids[i] = b[i].factId;
      versions[i] = b[i].version;
      paths[i] = b[i].pathArrayLiteral;
    }
    try (PreparedStatement ps =
        conn.prepareStatement(
            "SELECT t.header, t.payload FROM "
                + BenchSchema.V3
                + " t"
                + " JOIN unnest(?::uuid[], ?::int[], ?::text[]) AS k(fact_id, version, path_txt)"
                + " ON t.fact_id = k.fact_id AND t.version = k.version"
                + " AND t.path = k.path_txt::int[]")) {
      ps.setArray(1, conn.createArrayOf("varchar", ids));
      ps.setArray(2, conn.createArrayOf("int4", versions));
      ps.setArray(3, conn.createArrayOf("varchar", paths));
      drain(ps, bh);
    }
  }

  @Benchmark
  public void find_v3b(Blackhole bh) throws SQLException {
    Sample[] b = nextBatch();
    String[] ids = new String[b.length];
    String[] paths = new String[b.length];
    for (int i = 0; i < b.length; i++) {
      ids[i] = b[i].factId;
      paths[i] = b[i].pathArrayLiteral;
    }
    try (PreparedStatement ps =
        conn.prepareStatement(
            "SELECT t.header, t.payload FROM "
                + BenchSchema.V3B
                + " t"
                + " JOIN unnest(?::uuid[], ?::text[]) AS k(fact_id, path_txt)"
                + " ON t.fact_id = k.fact_id AND t.path = k.path_txt::int[]")) {
      ps.setArray(1, conn.createArrayOf("varchar", ids));
      ps.setArray(2, conn.createArrayOf("varchar", paths));
      drain(ps, bh);
    }
  }

  // ---------- insert (ON CONFLICT DO NOTHING) : steady-state dedup path ----------

  @Benchmark
  public void insert_v1() throws SQLException {
    Sample[] b = nextBatch();
    String sql =
        "INSERT INTO "
            + BenchSchema.V1
            + " (cache_key, header, payload)"
            + " VALUES (?, ?::jsonb, ?::jsonb) ON CONFLICT (cache_key) DO NOTHING";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      for (Sample s : b) {
        ps.setString(1, s.cacheKey);
        ps.setString(2, s.header);
        ps.setString(3, s.payload);
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  @Benchmark
  public void insert_v2() throws SQLException {
    Sample[] b = nextBatch();
    String sql =
        "INSERT INTO "
            + BenchSchema.V2
            + " (cache_key, fact_id, header, payload)"
            + " VALUES (?, ?::uuid, ?::jsonb, ?::jsonb) ON CONFLICT (cache_key) DO NOTHING";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      for (Sample s : b) {
        ps.setString(1, s.cacheKey);
        ps.setString(2, s.factId);
        ps.setString(3, s.header);
        ps.setString(4, s.payload);
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  @Benchmark
  public void insert_v3() throws SQLException {
    Sample[] b = nextBatch();
    String sql =
        "INSERT INTO "
            + BenchSchema.V3
            + " (fact_id, version, path, header, payload)"
            + " VALUES (?::uuid, ?, ?::int[], ?::jsonb, ?::jsonb)"
            + " ON CONFLICT (fact_id, version, path) DO NOTHING";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      for (Sample s : b) {
        ps.setString(1, s.factId);
        ps.setInt(2, s.version);
        ps.setString(3, s.pathArrayLiteral);
        ps.setString(4, s.header);
        ps.setString(5, s.payload);
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  // ---------- invalidate by ns+type (no supporting index in any variant) ----------

  @Benchmark
  public void deleteByNsType_v2() throws SQLException {
    Sample s = nextBatch()[0];
    inRolledBackTx(
        () -> {
          try (PreparedStatement ps =
              conn.prepareStatement(
                  "DELETE FROM "
                      + BenchSchema.V2
                      + " WHERE header ->> 'ns' = ? AND header ->> 'type' = ?")) {
            ps.setString(1, s.ns);
            ps.setString(2, s.type);
            ps.executeUpdate();
          }
        });
  }

  // ---------- invalidate by fact_id ----------

  @Benchmark
  public void deleteByFactId_v1() throws SQLException {
    Sample s = nextBatch()[0];
    inRolledBackTx(
        () -> {
          try (PreparedStatement ps =
              conn.prepareStatement("DELETE FROM " + BenchSchema.V1 + " WHERE cache_key LIKE ?")) {
            ps.setString(1, s.factId + "%");
            ps.executeUpdate();
          }
        });
  }

  @Benchmark
  public void deleteByFactId_v2() throws SQLException {
    deleteByFactIdEquals(BenchSchema.V2);
  }

  @Benchmark
  public void deleteByFactId_v3() throws SQLException {
    deleteByFactIdEquals(BenchSchema.V3);
  }

  @Benchmark
  public void deleteByFactId_v3b() throws SQLException {
    deleteByFactIdEquals(BenchSchema.V3B);
  }

  private void deleteByFactIdEquals(String table) throws SQLException {
    Sample s = nextBatch()[0];
    inRolledBackTx(
        () -> {
          try (PreparedStatement ps =
              conn.prepareStatement("DELETE FROM " + table + " WHERE fact_id = ?::uuid")) {
            ps.setString(1, s.factId);
            ps.executeUpdate();
          }
        });
  }

  // ---------- helpers ----------

  private void drain(PreparedStatement ps, Blackhole bh) throws SQLException {
    try (ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        bh.consume(rs.getString(1)); // force header materialization
        bh.consume(rs.getString(2)); // force payload (TOAST) materialization
      }
    }
  }

  @FunctionalInterface
  private interface SqlOp {
    void run() throws SQLException;
  }

  /** runs a mutation and rolls it back, so the dataset is unchanged between invocations */
  private void inRolledBackTx(SqlOp op) throws SQLException {
    conn.setAutoCommit(false);
    try {
      op.run();
    } finally {
      conn.rollback();
      conn.setAutoCommit(true);
    }
  }
}
