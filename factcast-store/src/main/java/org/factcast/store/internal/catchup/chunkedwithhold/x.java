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
package org.factcast.store.internal.catchup.chunkedwithhold;

import com.google.common.base.Stopwatch;
import java.sql.*;
import java.util.concurrent.*;
import lombok.SneakyThrows;
import org.postgresql.jdbc.PgConnection;

public class x {
  private static CountDownLatch cdl = new CountDownLatch(1);

  @SneakyThrows
  public static void main(String[] args) {
    Connection c =
        DriverManager.getConnection("jdbc:postgresql://localhost/postgres?user=doc&password=doc");

    CompletableFuture.runAsync(
        () -> {
          try {
            Stopwatch sw = Stopwatch.createStarted();
            ResultSet rs =
                c.createStatement()
                    .executeQuery("select sum(1) from generate_series(100,100000000);");
            //  .executeQuery("select 42");
            rs.next();
            System.out.println(rs.getLong(1));
            System.out.println(sw.stop().elapsed());
          } catch (Throwable e) {
            e.printStackTrace();
          }
          cdl.countDown();
        });

    Thread.sleep(1000);

    c.unwrap(PgConnection.class).cancelQuery();

    cdl.await();
  }
}
