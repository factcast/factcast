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
package org.factcast.store;

import static java.util.stream.IntStream.range;

import com.google.common.base.Stopwatch;
import java.sql.*;
import java.util.List;
import java.util.concurrent.*;
import lombok.SneakyThrows;
import org.postgresql.Driver;

// TODO remove from codebase

public class JdbcInsertionBenchmark implements Runnable {
  private Connection conn;
  static String url = "jdbc:postgresql://localhost:5432/postgres";
  static String username = "doc";
  static String password = "doc";

  @SneakyThrows
  public static void main(String[] args) {

    DriverManager.registerDriver(new Driver());

    Stopwatch st = Stopwatch.createStarted();

    List<JdbcInsertionBenchmark> tasks =
        range(0, 50).mapToObj(i -> new JdbcInsertionBenchmark()).toList();
    initialize();

    ExecutorService es = Executors.newFixedThreadPool(1);

    for (JdbcInsertionBenchmark task : tasks) {
      es.submit(task);
    }

    es.shutdown();
    es.awaitTermination(10, TimeUnit.MINUTES);
    System.out.println(st.stop().elapsed(TimeUnit.MILLISECONDS) + "ms");
  }

  @SneakyThrows
  private static void initialize() {
    Connection c = DriverManager.getConnection(url, username, password);
    c.prepareStatement(
            "truncate notification; truncate interval_cleanup;truncate interval_notify; ")
        .execute();
  }

  @SneakyThrows
  JdbcInsertionBenchmark() {

    this.conn = DriverManager.getConnection(url, username, password);
  }

  @SneakyThrows
  @Override
  public void run() {
    for (int i = 0; i < 1000; i++) {
      conn.setAutoCommit(true);
      try (PreparedStatement st =
          conn.prepareStatement("insert into notification(ns,type) values ('x','y')")) {
        int i1 = st.executeUpdate();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
